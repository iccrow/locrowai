import numpy as np
from pydantic import BaseModel
import io
import soundfile as sf

from api import Function, register

class PCMConvertParams(BaseModel):
    audio: bytes

class PCMConvertReturns(BaseModel):
    audio: bytes

@register("/audio/wav_to_pcm")
class PCMConvertFunc(Function[PCMConvertParams, PCMConvertReturns]):

    def exec(self):
        audio_io = io.BytesIO(self.params.audio)
        data, _ = sf.read(audio_io, dtype='int16')

        self.returns = PCMConvertReturns(audio=data.tobytes())
    
    @staticmethod
    def warmup():
        audio = io.BytesIO()
        sf.write(audio, [0.0], 16000, format='WAV', subtype='PCM_16')
        params = PCMConvertParams(
            audio=audio.getvalue()
        )
        func = PCMConvertFunc(params=params)
        func.exec()