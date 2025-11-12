from pydantic import BaseModel
from typing import List
from api import Function, register
from .model_loader import get_llm


class Message(BaseModel):
    role: str
    content: str


class ChatParams(BaseModel):
    temperature: float = 0.7
    messages: List[Message]


class ChatReturns(BaseModel):
    role: str
    content: str


@register("/llm/chat")
class ChatFunc(Function[ChatParams, ChatReturns]):

    def exec(self):
        llm = get_llm()
        if llm is None:
            raise RuntimeError("No LLM model is loaded.")

        messages = [
            {"role": message.role, "content": message.content}
            for message in self.params.messages
        ]

        res = llm.create_chat_completion_openai_v1(
            messages=messages,
            temperature=self.params.temperature,
        )

        msg = res.choices[0].message

        self.returns = ChatReturns(role=msg.role, content=msg.content)

    @staticmethod
    def warmup():
        params = ChatParams(
            temperature=0.7,
            messages=[
                Message(role="user", content="Hello, how are you?")
            ]
        )
        func = ChatFunc(params=params)
        func.exec()
