from .rvc import rvc
import soundfile as sf
import numpy as np
import os, tempfile, io
from pydantic import BaseModel

from api import Function, register

class InferParams(BaseModel):
    audio: bytes
    model: str

class InferReturns(BaseModel):
    audio: bytes

@register("/rvc/infer")
class InferFunc(Function[InferParams, InferReturns]):

    def exec(self):
        if rvc.current_model != self.params.model + '.pth':
            rvc.load_model(f'extensions/rvc/models/{self.params.model}.pth', index_path=f'extensions/rvc/models/{self.params.model}.index')

        with tempfile.TemporaryDirectory() as tmpdir:
            in_path = os.path.join(tmpdir, "in.wav")
            out_path = os.path.join(tmpdir, "out.wav")

            with open(in_path, "wb") as f:
                f.write(self.params.audio)
            
            rvc.infer_file(in_path, out_path)

            audio, sr = sf.read(out_path, dtype='float32')

            if audio.ndim > 1:
                audio = np.mean(audio, axis=1)

            buf = io.BytesIO()
            sf.write(buf, audio, samplerate=sr, format="WAV")
            self.returns = InferReturns(audio=buf.getvalue())