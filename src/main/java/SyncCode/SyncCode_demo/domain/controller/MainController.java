package SyncCode.SyncCode_demo.domain.controller;

import SyncCode.SyncCode_demo.domain.rtc.KurentoRoom;
import SyncCode.SyncCode_demo.domain.rtc.KurentoRoomManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class MainController {
    private final KurentoRoomManager roomManager;

    @GetMapping("/")
    public String mainPage(){
        return "home";
    }

    @PostMapping("/login")
    public String login(HttpServletRequest request, @RequestParam(name="name") String name, Model model){
        HttpSession session = request.getSession(true);
        session.setAttribute("userName", name);
        System.out.println(name);

        return "redirect:/rooms";
    }

    @GetMapping("/rooms")
    public String getRooms(HttpServletRequest request, Model model) {
        List<KurentoRoom> rooms = roomManager.getRooms();
        model.addAttribute("rooms", rooms);
        return "rooms";
    }

    @GetMapping("/createRoomForm")
    public String getCreateRoomForm(){
        return "createRoom";
    }

    @PostMapping("/createRoom")
    public String createRoom(@RequestParam(name = "roomName") String roomName) {
        roomManager.createRoom(roomName);
        return "redirect:/rooms";
    }

    @GetMapping("/joinRoom")
    public String joinRoom(HttpServletRequest request, Model model, @RequestParam(name = "roomId") String roomId) {
        HttpSession session = request.getSession(false);
        String userName = (String) session.getAttribute("userName");
        model.addAttribute("userName", userName);
        model.addAttribute("roomId", roomId);
//        return "video";
        return "video-loopback";
    }
}
