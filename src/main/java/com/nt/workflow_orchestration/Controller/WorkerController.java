package com.nt.workflow_orchestration.Controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkerController {


    @GetMapping("/payment-service")
    public Map<String,String> paymentService(){

        Map<String,String> response =
                new HashMap<>();

        response.put("status","success");
        response.put("message","Payment processed successfully");

        return response;

    }



    @GetMapping("/inventory-service")
    public Map<String,String> inventoryService(){

        Map<String,String> response =
                new HashMap<>();

        response.put("status","success");
        response.put("message","Inventory checked and reserved");

        return response;

    }



    @GetMapping("/notification-service")
    public Map<String,String> notificationService(){

        Map<String,String> response =
                new HashMap<>();

        response.put("status","success");
        response.put("message","Notification sent to customer");

        return response;

    }


    @GetMapping("/shipping-service")
    public Map<String,String> shippingService(){

        Map<String,String> response =
                new HashMap<>();

        response.put("status","success");
        response.put("message","Shipping label created and dispatched");

        return response;

    }

}