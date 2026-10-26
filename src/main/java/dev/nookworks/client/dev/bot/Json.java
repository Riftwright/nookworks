package dev.nookworks.client.dev.bot;

/** A small JSON writer for the walker's state and event files (no library: the shapes are few and flat). */
final class Json {
    private final StringBuilder out = new StringBuilder();
    private boolean first = true;

    Json() {
        this.out.append('{');
    }

    private void key(String key) {
        if (!this.first) {
            this.out.append(',');
        }
        this.first = false;
        quote(this.out, key);
        this.out.append(':');
    }

    Json put(String key, String value) {
        this.key(key);
        if (value == null) {
            this.out.append("null");
        } else {
            quote(this.out, value);
        }
        return this;
    }

    Json put(String key, double value) {
        this.key(key);
        if (Double.isFinite(value)) {
            this.out.append(Math.round(value * 100.0) / 100.0);
        } else {
            this.out.append("null");
        }
        return this;
    }

    Json put(String key, long value) {
        this.key(key);
        this.out.append(value);
        return this;
    }

    Json put(String key, boolean value) {
        this.key(key);
        this.out.append(value);
        return this;
    }

    /** A value already written as JSON (an array, an object). */
    Json raw(String key, String json) {
        this.key(key);
        this.out.append(json);
        return this;
    }

    @Override
    public String toString() {
        return this.out + "}";
    }

    static String array(Iterable<String> items) {
        StringBuilder b = new StringBuilder("[");
        boolean first = true;
        for (String item : items) {
            if (!first) {
                b.append(',');
            }
            first = false;
            b.append(item);
        }
        return b.append(']').toString();
    }

    static String str(String s) {
        StringBuilder b = new StringBuilder();
        quote(b, s);
        return b.toString();
    }

    private static void quote(StringBuilder b, String s) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        b.append(String.format("\\u%04x", (int) ch));
                    } else {
                        b.append(ch);
                    }
                }
            }
        }
        b.append('"');
    }
}
