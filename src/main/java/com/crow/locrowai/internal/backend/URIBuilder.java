package com.crow.locrowai.internal.backend;

final class URIBuilder {

    private static final String base = "https://github.com/astral-sh/python-build-standalone/releases/download";
    private static final String PY_VERSION = "3.10.19";
    private static final String PY_RELEASE = "20251031";

    static String buildPythonUrl() {
        return String.format(
                "%s/%s/cpython-%s-install_only_stripped.tar.gz",
                trimSlash(base),
                PY_RELEASE,
                getPythonTags()
        );
    }

    static String getPythonTags() {
        SystemProbe.ProbeResult res = SystemProbe.result;
        String os = SystemProbe.osKey(res.os());
        String arch = SystemProbe.archKey(res.os().arch());
        String vendor;

        // Fallback to ensure a valid path every time:
        if (arch.equals("unknown")) arch = "x86_64";

        // Determine vendor triplet component
        switch (os) {
            case "windows" -> vendor = "pc-windows-msvc";
            case "linux" -> vendor = "unknown-linux-gnu";
            case "macos" -> vendor = "apple-darwin";
            default -> vendor = "unknown-linux-gnu"; // safe fallback
        }

        return String.format(
                "%s+%s-%s-%s",
                PY_VERSION,
                PY_RELEASE,
                arch,
                vendor
        );
    }


    private static String trimSlash(String s) {
        if (s.endsWith("/")) return s.substring(0, s.length() - 1);
        return s;
    }
}
