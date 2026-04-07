package lingzhou.agent.backend.business.model.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lingzhou.agent.backend.business.model.service.ModelLibraryService;
import lingzhou.agent.backend.common.lzException.TaskException;
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

    @PostMapping("/vendors")
    public ModelLibraryService.VendorView createVendor(
            @RequestBody ModelLibraryService.UpsertVendorRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        return modelLibraryService.createVendor(resolveCurrentUserId(httpRequest), request);
    }

    @PutMapping("/vendors/{id}")
    public ModelLibraryService.VendorView updateVendor(
            @PathVariable("id") Long id,
            @RequestBody ModelLibraryService.UpsertVendorRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        return modelLibraryService.updateVendor(resolveCurrentUserId(httpRequest), id, request);
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

    @PostMapping("/models")
    public ModelLibraryService.ModelView createModel(
            @RequestBody ModelLibraryService.UpsertModelRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        return modelLibraryService.createModel(resolveCurrentUserId(httpRequest), request);
    }

    @PutMapping("/models/{id}")
    public ModelLibraryService.ModelView updateModel(
            @PathVariable("id") Long id,
            @RequestBody ModelLibraryService.UpsertModelRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        return modelLibraryService.updateModel(resolveCurrentUserId(httpRequest), id, request);
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
