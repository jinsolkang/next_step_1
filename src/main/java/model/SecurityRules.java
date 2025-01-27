package model;

import java.util.HashSet;
import java.util.Set;

public class SecurityRules {
    private static final Set<String> protectedPages = new HashSet<>();

    static {
        protectedPages.add("/user/list.html");
    }

    public static boolean isProtectedPage(String page) {
        return protectedPages.contains(page);
    }
}
