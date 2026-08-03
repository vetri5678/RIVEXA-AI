package ai.riskvision.graveyard.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaWebController {

    @GetMapping(value = {
        "/",
        "/dashboard",
        "/projects",
        "/profile",
        "/settings",
        "/reports",
        "/login",
        "/register",
        "/oauth2/callback",
        "/oauth2/email-required"
    })
    public String forwardSpaRoutes() {
        return "forward:/index.html";
    }
}
