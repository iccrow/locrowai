package com.crow.locrowai.api.runtime;

import com.crow.locrowai.api.registration.RegistrationExample;

import java.util.List;
import java.util.Map;

import static com.crow.locrowai.api.registration.RegistrationExample.*;

public class RuntimeExample {
    public static void run() {

        ScriptBuilder scriptBuilder = new ScriptBuilder();

        scriptBuilder.then(new Call() // create a new call without an id
                        // run the "/llm/chat" endpoint function
                        .call("/llm/chat")
                        // pass a value of "0.7" to the function parameter "temperature"
                        .feed("temperature", 0.7)
                        .feed("messages", List.of(Map.of("role", "user", "content", "How are you?"))))
                // create a new call with an id of "tts"
                .then(new Call("tts")
                        .call("/tts")
                        .feed("voice", "am_puck")
                        .feed("speed", 1.1)
                        // pass the return "content" from the previously executed call to the function parameter "text"
                        .feed("text", "{{ ^content }}"))
                .then(new Call()
                        .call("/rvc/infer")
                        .feed("model", "villager")
                        .feed("audio", "{{ ^audio }}"))
                .then(new Call()
                        .call("/audio/pcm_convert")
                        .feed("audio", "{{ ^audio }}"))
                .then(new Call()
                        .call("/base64/encode")
                        .feed("bytes", "{{ ^audio }}"))
                // return the result "base64" from the last executed call as "audio"
                .returns("audio", "{{ ^base64 }}")
                // return the result "timestamps" from the call of id "tts" as "transcript"
                .returns("transcript", "{{ tts.timestamps }}");

        // finish building the script
        Script script = scriptBuilder.build();
        System.out.println(script.getJsonBlueprint());

        // run the script and print the "transcript" result when they arrive
        context.execute(script).thenAccept(results -> System.out.println(results.get("transcript").getAsString()))
                .exceptionally(err -> null);
    }
}
