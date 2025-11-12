import soundfile as sf
from pydantic import BaseModel
import uuid
import io
import os

from api import Function, register

class CacheParams(BaseModel):
    audio: bytes
    auto_cleanup: bool = True

class CacheReturns(BaseModel):
    path: str

@register("/audio/wav_cache")
class CacheFunc(Function[CacheParams, CacheReturns]):

    def exec(self):
        path = f"extensions/audio/cache/{uuid.uuid4()}.wav"
        os.makedirs(os.path.dirname(path), exist_ok=True)
        
        data, sr = sf.read(io.BytesIO(self.params.audio))  # decodes WAV bytes
        sf.write(path, data, sr)
        
        self.returns = CacheReturns(path=path)

    def cleanup(self):
        if self.params.auto_cleanup:
            os.remove(self.returns.path)

    @staticmethod
    def warmup():
        audio = io.BytesIO()
        sf.write(audio, [0.0], 16000, format='WAV', subtype='PCM_16')
        params = CacheParams(audio=audio.getvalue(), auto_cleanup=True)
        func = CacheFunc(params=params)
        func.exec()
        func.cleanup()