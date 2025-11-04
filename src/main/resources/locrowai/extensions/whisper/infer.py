from pydantic import BaseModel

from api import Function, register
from .whisper import model

class InferParams(BaseModel):
    path: str
    beam_size: int = 1
    language: str = None

class InferReturns(BaseModel):
    transcript: str
    timestamps: list[dict]
    language: str
    language_probability: float

@register("/whisper/infer")
class InferFunc(Function[InferParams, InferReturns]):

    def exec(self):
        segments, info = model.transcribe(self.params.path, beam_size=self.params.beam_size, language=self.params.language)
        
        transcript = ""
        timestamps = []

        for segment in segments:
            transcript += segment.text
            timestamps.append({
                "start": segment.start,
                "end": segment.end,
                "text": segment.text
            })

        self.returns = InferReturns(transcript=transcript, timestamps=timestamps, language=info.language, language_probability=info.language_probability)