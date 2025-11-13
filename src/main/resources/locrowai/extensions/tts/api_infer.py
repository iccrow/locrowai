import soundfile as sf
from pydantic import BaseModel
import numpy as np
import io

from api.extensions import Function, register
from api.threading import ModelLock
from .model_loader import get_pipeline

class TTSParams(BaseModel):
    text: str
    voice: str
    speed: float

class TTSReturns(BaseModel):
    audio: bytes
    timestamps: list[dict]

@register("/tts")
class TTSFunc(Function[TTSParams, TTSReturns]):
    
    def exec(self):
        pipeline = get_pipeline()
        if pipeline is None:
            raise RuntimeError("No TTS model is loaded.")
        
        generator = pipeline(
            self.params.text, voice=self.params.voice,
            speed=self.params.speed
        )

        chunks = []
        timestamps = []

        with ModelLock("tts"):
            for result in generator:
                audio = result.audio.cpu().numpy()  # Convert to numpy
                chunks.append(audio[:int(-24000*0.4)].tobytes())  # Append byte data
                tokens = result.tokens
                for t in tokens:
                    timestamps.append({
                        "start": t.start_ts,
                        "end": t.end_ts,
                        "text": t.text + t.whitespace
                    })

        chunks = b''.join(chunks)

        buffer = io.BytesIO()
        audio = np.frombuffer(chunks, dtype=np.float32)
        sf.write(buffer, audio, 24000, format="WAV")

        self.returns = TTSReturns(audio = buffer.getvalue(), timestamps=timestamps)

    @staticmethod
    def warmup():
        params = TTSParams(
            text="Hello, this is a warmup test.",
            voice="am_michael",
            speed=1.0
        )
        func = TTSFunc(params=params)
        func.exec()