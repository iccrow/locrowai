from pydantic import BaseModel, ConfigDict
from torch import Tensor
import torchaudio

from api import Function, register
from .classifier_loader import get_classifier

class EmbedParams(BaseModel):
    path: str

class EmbedReturns(BaseModel):
    model_config = ConfigDict(arbitrary_types_allowed=True)
    embeddings: Tensor

@register("/audio/embed")
class EmbedFunc(Function[EmbedParams, EmbedReturns]):

    def exec(self):
        model = get_classifier()
        if model is None:
            raise RuntimeError("No speaker embedding model is loaded.")
        
        signal, fs = torchaudio.load(self.params.path)
        embeddings = model.encode_batch(signal)

        self.returns = EmbedReturns(embeddings=embeddings)
    
    @staticmethod
    def warmup():
        params = EmbedParams(path="sample/audio.wav")
        func = EmbedFunc(params=params)
        func.exec()