package com.tpm.realtime;

import java.util.regex.Pattern;

/** STOMP destination naming in one place. */
public final class Destinations {

    public static final String NOTIFICATIONS_QUEUE = "/queue/notifications";
    private static final Pattern PROJECT_TOPIC = Pattern.compile("^/(?:topic|app)/projects/(\\d+)(?:/presence)?$");

    private Destinations() {}

    public static String project(Long projectId) {
        return "/topic/projects/" + projectId;
    }

    public static String presence(Long projectId) {
        return "/topic/projects/" + projectId + "/presence";
    }

    /** @return the project id addressed by a topic, or {@code null} if it isn't a project topic. */
    public static Long projectIdOf(String destination) {
        if (destination == null) return null;
        var m = PROJECT_TOPIC.matcher(destination);
        return m.matches() ? Long.valueOf(m.group(1)) : null;
    }
}
