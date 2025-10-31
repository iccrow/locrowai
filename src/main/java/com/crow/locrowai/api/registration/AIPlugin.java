package com.crow.locrowai.api.registration;

import com.crow.locrowai.api.AIContext;

public interface AIPlugin {

    default void register(AIContext context) {}
}
