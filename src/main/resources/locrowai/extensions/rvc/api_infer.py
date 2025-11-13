from .model_loader import get_rvc
import soundfile as sf
import numpy as np
import os, tempfile, io
from pydantic import BaseModel

from api.extensions import Function, register
from api.threading import ModelLock

class InferParams(BaseModel):
    audio: bytes

class InferReturns(BaseModel):
    audio: bytes

@register("/rvc/infer")
class InferFunc(Function[InferParams, InferReturns]):

    def exec(self):
        rvc = get_rvc()
        if rvc is None:
            raise RuntimeError("No RVC model is loaded.")

        with tempfile.TemporaryDirectory() as tmpdir:
            in_path = os.path.join(tmpdir, "in.wav")
            out_path = os.path.join(tmpdir, "out.wav")

            with open(in_path, "wb") as f:
                f.write(self.params.audio)
            
            with ModelLock("rvc"):
                rvc.infer_file(in_path, out_path)

            audio, sr = sf.read(out_path, dtype='float32')

            if audio.ndim > 1:
                audio = np.mean(audio, axis=1)

            buf = io.BytesIO()
            sf.write(buf, audio, samplerate=sr, format="WAV")
            self.returns = InferReturns(audio=buf.getvalue())
    
    @staticmethod
    def warmup():
        buf = io.BytesIO()
        audio, sr = sf.read("sample/speaker.wav", dtype='float32')
        sf.write(buf, audio, samplerate=sr, format="WAV")
        params = InferParams(
            audio=buf.getvalue(),
            model='villager'
        )
        func = InferFunc(params=params)
        func.exec()