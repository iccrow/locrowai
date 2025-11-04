import numpy as np
from pydantic import BaseModel
import uuid
import os
import soundfile as sf

from api import Function, register

class PCMCacheParams(BaseModel):
    audio: bytes       # raw PCM bytes
    samplerate: int    # e.g., 44100
    channels: int = 1  # 1 = mono, 2 = stereo
    dtype: str = "int16"  # "int16" or "float32"
    auto_cleanup: bool = True

class PCMCacheReturns(BaseModel):
    path: str

@register("/audio/pcm_cache")
class PCMCacheFunc(Function[PCMCacheParams, PCMCacheReturns]):

    def exec(self):
        path = f"extensions/audio/cache/{uuid.uuid4()}.wav"

        # Convert raw PCM bytes to numpy array
        dtype = np.int16 if self.params.dtype == "int16" else np.float32
        data = np.frombuffer(self.params.audio, dtype=dtype)

        # Reshape to (samples, channels) if stereo
        if self.params.channels > 1:
            data = data.reshape(-1, self.params.channels)

        # Save as WAV
        sf.write(path, data, self.params.samplerate)

        self.returns = PCMCacheReturns(path=path)

    def cleanup(self):
        if self.params.auto_cleanup:
            os.remove(self.returns.path)
