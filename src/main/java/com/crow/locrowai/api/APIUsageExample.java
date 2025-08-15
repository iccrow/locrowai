package com.crow.locrowai.api;

import java.util.List;
import java.util.Map;

public class APIUsageExample {
    public static void run() {
        ScriptBuilder scriptBuilder = new ScriptBuilder();

        scriptBuilder.then(new FunctionBuilder("/llm/chat")
                .feed("temperature", 0.7)
                .feed("messages", List.of(Map.of("role", "user",
                        "content", "How are you?")))
                .feedReturn("content", "text")
                .passReturn("content", "script"))
                .then(new FunctionBuilder("/tts")
                        .feed("voice", "am_puck")
                        .feed("speed", 1.1)
                        .feedReturn("audio", "audio")
                        .pass("script"))
                .then(new FunctionBuilder("/rvc/infer")
                        .feed("model", "villager")
                        .feedReturn("audio", "audio")
                        .feedReturn("samplerate", "original_sr")
                        .pass("script"))
                .then(new FunctionBuilder("/audio/resample")
                        .feed("target_sr", 48000)
                        .feedReturn("audio", "audio")
                        .pass("script"))
                .then(new FunctionBuilder("/audio/pcm_convert")
                        .feedReturn("audio", "bytes")
                        .pass("script"))
                .then(new FunctionBuilder("/json_serializer/bytes"))
                .returns("base64")
                .returns("script");

        Script script = scriptBuilder.build();
        System.out.println(script.getJsonBlueprint());

        script.execute(results -> {
            System.out.println(results.get("script").getAsString());
        });
    }
}
