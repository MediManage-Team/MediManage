package org.example.MediManage.service;

import org.example.MediManage.dao.UserDAO;
import org.example.MediManage.model.User;

import java.sql.SQLException;

public class AuthService {

    public static boolean login(String username, String password) {
        try {
            User authenticated = new UserDAO().authenticate(username, password);
            return authenticated != null;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
