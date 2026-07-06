package lingzhou.agent.backend.business.model.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lingzhou.agent.backend.business.model.service.ModelLibraryService;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/model-library")
public class ModelLibraryController {

    private final ModelLibraryService modelLibraryService;

    public ModelLibraryController(ModelLibraryService modelLibraryService) {
        this.modelLibraryService = modelLibraryService;
    }

    @GetMapping("/vendors")
    public List<ModelLibraryService.VendorView> listVendors(HttpServletRequest request) throws TaskException {
        return modelLibraryService.listVendors(resolveCurrentUserId(request));
    }

    @PutMapping("/vendors/{id}")
    public ModelLibraryService.VendorView updateVendor(
            @PathVariable("id") Long id,
            @RequestBody ModelLibraryService.UpsertVendorRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        return modelLibraryService.updateVendor(resolveCurrentUserId(httpRequest), id, request);
    }

    @PostMapping("/vendors/{id}/validate")
    public ModelLibraryService.VendorValidationView validateVendor(
            @PathVariable("id") Long id,
            @RequestBody ModelLibraryService.UpsertVendorRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        return modelLibraryService.validateVendor(resolveCurrentUserId(httpRequest), id, request);
    }

    @GetMapping("/models")
    public List<ModelLibraryService.ModelView> listModels(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "capabilityType", required = false) String capabilityType,
            @RequestParam(value = "vendorId", required = false) Long vendorId,
            @RequestParam(value = "status", required = false) String status,
            HttpServletRequest request)
            throws TaskException {
        return modelLibraryService.listModels(resolveCurrentUserId(request), keyword, capabilityType, vendorId, status);
    }

    @GetMapping("/chat-options")
    public List<ModelLibraryService.ChatModelOptionView> listAvailableChatModels() {
        return modelLibraryService.listAvailableChatModels();
    }

    @PostMapping("/models")
    public ModelLibraryService.ModelView createModel(
            @RequestBody ModelLibraryService.UpsertModelRequest request, HttpServletRequest httpRequest)
            throws TaskException {
        return modelLibraryService.createModel(resolveCurrentUserId(httpRequest), request);
    }

    @PostMapping("/models/validate")
    public ModelLibraryService.ValidationResult validateModel(
            @RequestBody ModelLibraryService.UpsertModelRequest request, HttpServletRequest httpRequest)
            throws TaskException {
        return modelLibraryService.validateModel(resolveCurrentUserId(httpRequest), request);
    }

    @PutMapping("/models/{id}")
    public ModelLibraryService.ModelView updateModel(
            @PathVariable("id") Long id,
            @RequestBody ModelLibraryService.UpsertModelRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        return modelLibraryService.updateModel(resolveCurrentUserId(httpRequest), id, request);
    }

    @DeleteMapping("/models/{id}")
    public void deleteModel(@PathVariable("id") Long id, HttpServletRequest httpRequest) throws TaskException {
        modelLibraryService.deleteModel(resolveCurrentUserId(httpRequest), id);
    }

    @GetMapping("/defaults")
    public List<ModelLibraryService.DefaultBindingView> listDefaults(HttpServletRequest request) throws TaskException {
        return modelLibraryService.listDefaults(resolveCurrentUserId(request));
    }

    @PostMapping("/defaults/{capabilityType}")
    public ModelLibraryService.DefaultBindingView saveDefaultBinding(
            @PathVariable("capabilityType") String capabilityType,
            @RequestBody(required = false) ModelLibraryService.DefaultBindingRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        return modelLibraryService.saveDefaultBinding(resolveCurrentUserId(httpRequest), capabilityType, request);
    }

    private static Long resolveCurrentUserId(HttpServletRequest request) {
        Object userIdValue = request.getAttribute("UserId");
        if (userIdValue == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(userIdValue));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
