package ua.kiev.prog;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class MyController {
    static final int DEFAULT_GROUP_ID = -1;
    static final int ITEMS_PER_PAGE = 6;

    private final ContactService contactService;

    public MyController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping("/")
    public String index(Model model,
                        @RequestParam(required = false, defaultValue = "0") Integer page) {
        if (page < 0) page = 0;

        List<Contact> contacts = contactService
                .findAll(PageRequest.of(page, ITEMS_PER_PAGE, Sort.Direction.DESC, "id"));

        model.addAttribute("groups", contactService.findGroups());
        model.addAttribute("contacts", contacts);
        model.addAttribute("allPages", getPageCount());

        return "index";
    }

    @GetMapping("/reset")
    public String reset() {
        contactService.reset();
        return "redirect:/";
    }

    @GetMapping("/contact_add_page")
    public String contactAddPage(Model model) {
        model.addAttribute("groups", contactService.findGroups());
        return "contact_add_page";
    }

    @GetMapping("/group_add_page")
    public String groupAddPage() {
        return "group_add_page";
    }

    @GetMapping("/group/{id}")
    public String listGroup(
            @PathVariable(value = "id") long groupId,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            Model model)
    {
        Group group = (groupId != DEFAULT_GROUP_ID) ? contactService.findGroup(groupId) : null;
        if (page < 0) page = 0;

        List<Contact> contacts = contactService
                .findByGroup(group, PageRequest.of(page, ITEMS_PER_PAGE, Sort.Direction.DESC, "id"));

        model.addAttribute("groups", contactService.findGroups());
        model.addAttribute("contacts", contacts);
        model.addAttribute("byGroupPages", getPageCount(group));
        model.addAttribute("groupId", groupId);

        return "index";
    }

    @PostMapping(value = "/search")
    public String search(@RequestParam String pattern, Model model,
                         @RequestParam(required = false, defaultValue = "0") Integer page) {
        if (page < 0) page = 0;

        List<Contact> contacts = contactService.findByPattern(pattern,
                PageRequest.of(page, ITEMS_PER_PAGE, Sort.Direction.DESC, "id"));

        model.addAttribute("groups", contactService.findGroups());
        model.addAttribute("contacts", contacts);
        model.addAttribute("allPages", getPageCount());

        return "index";
    }

    @PostMapping(value = "/contact/delete")
    public ResponseEntity<Void> delete(
            @RequestParam(value = "toDelete[]", required = false) long[] toDelete) {
        if (toDelete != null && toDelete.length > 0)
            contactService.deleteContacts(toDelete);

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping(value="/contact/add")
    public String contactAdd(@RequestParam(value = "group") long groupId,
                             @RequestParam String name,
                             @RequestParam String surname,
                             @RequestParam String phone,
                             @RequestParam String email)
    {
        Group group = (groupId != DEFAULT_GROUP_ID) ? contactService.findGroup(groupId) : null;

        Contact contact = new Contact(group, name, surname, phone, email);
        contactService.addContact(contact);

        return "redirect:/";
    }

    @PostMapping(value="/group/add")
    public String groupAdd(@RequestParam String name) {
        contactService.addGroup(new Group(name));
        return "redirect:/";
    }

    private long getPageCount() {
        long totalCount = contactService.count();
        return (totalCount / ITEMS_PER_PAGE) + ((totalCount % ITEMS_PER_PAGE > 0) ? 1 : 0);
    }

    private long getPageCount(Group group) {
        long totalCount = contactService.countByGroup(group);
        return (totalCount / ITEMS_PER_PAGE) + ((totalCount % ITEMS_PER_PAGE > 0) ? 1 : 0);
    }


    @GetMapping("/export/csv")
    public ResponseEntity<String> exportToCSV(HttpServletResponse response) {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=contacts.csv");

        List<Contact> contacts = contactService.findAllContacts();

        try (PrintWriter writer = response.getWriter()) {
            writer.println("Name,Surname,Phone,Email,Group");

            for (Contact contact : contacts) {
                writer.println(
                        contact.getName() + "," +
                                contact.getSurname() + "," +
                                contact.getPhone() + "," +
                                contact.getEmail() + "," +
                                (contact.getGroup() != null ? contact.getGroup().getName() : "")
                );
            }
            return ResponseEntity.ok("CSV file generated successfully.");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error generating CSV file.");
        }
    }

    @PostMapping(value = "/contact/import")
    @ResponseBody
    public ResponseEntity<String> importContacts(@RequestParam("file") MultipartFile file) {
        try {
            List<Contact> importedContacts = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()));
            String line;
            reader.readLine();

            while ((line = reader.readLine()) != null) {
                String[] data = line.split(",");
                if (data.length >= 4) {
                    String name = data[0];
                    String surname = data[1];
                    String phone = data[2];
                    String email = data[3];

                    Contact contact = new Contact(name, surname, phone, email);


                    importedContacts.add(contact);
                }
            }

            contactService.saveAll(importedContacts);

            return ResponseEntity.ok("CSV file imported successfully.");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error importing CSV file.");
        }
    }

}
