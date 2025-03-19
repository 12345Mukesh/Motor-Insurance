//package com.api.products.controller;
//
//
//import org.springframework.web.bind.annotation.*;
//
//@RestController
//@RequestMapping("/api/users")
//public class UsersController
//{
//
//    @GetMapping("/all")
//    public String getUsers()
//    {
//        return "Get request was sent";
//    }
//
//    //path parameter
//    @GetMapping("/{userID}")
//    public String getUser(@PathVariable String userID)
//    {
//        return "Get request was sent for userID: " + userID;
//    }
//
//
//    //query parameter
//    @GetMapping()
//    public String getSingleUser(@RequestParam(value = "page") int pageno, @RequestParam(value = "limit") int limitno)
//    {
//        return "Get request was sent for page: " + pageno + " and limit is: " +limitno ;
//    }
//    @PostMapping
//    public String createUser()
//    {
//        return "Post request was sent";
//    }
//
//    @PutMapping
//    public String updateUser()
//    {
//        return "Put request was sent";
//    }
//
//    @DeleteMapping
//    public String deleteUser()
//    {
//        return "Delete request was sent";
//    }
//
//
//}
