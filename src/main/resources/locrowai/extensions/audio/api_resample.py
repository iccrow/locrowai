import librosa
import numpy as np
from pydantic import BaseModel

from api.extensions import Function, register

class ResampleParams(BaseModel):
    audio: bytes
    original_sr: int
    target_sr: int

class ResampleReturns(BaseModel):
    audio: bytes

@register("/audio/resample")
class ResampleFunc(Function[ResampleParams, ResampleReturns]):

    def exec(self):
        audio = np.frombuffer(self.params.audio, dtype=np.float32)
        audio = librosa.resample(audio, orig_sr=self.params.original_sr, target_sr=self.params.target_sr)

        self.returns = ResampleReturns(audio=audio.tobytes())
    
    @staticmethod
    def warmup():
        params = ResampleParams(
            audio=(np.zeros(16000, dtype=np.float32)).tobytes(),
            original_sr=16000,
            target_sr=8000
        )
        func = ResampleFunc(params=params)
        func.exec()