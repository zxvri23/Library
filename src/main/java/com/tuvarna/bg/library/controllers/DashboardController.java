package com.tuvarna.bg.library.controllers;

import com.tuvarna.bg.library.entity.UserEntity;

public interface DashboardController {
    void setCurrentUser(UserEntity user);
    UserEntity getCurrentUser();
}
