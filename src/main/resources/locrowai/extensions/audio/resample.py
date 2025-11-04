import librosa
import numpy as np
from pydantic import BaseModel

from api import Function, register

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