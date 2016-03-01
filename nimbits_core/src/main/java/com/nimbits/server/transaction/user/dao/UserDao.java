/*
 * Copyright 2016 Benjamin Sautner
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.nimbits.server.transaction.user.dao;

import com.nimbits.client.model.user.User;
import com.nimbits.server.orm.socket.SocketStore;

import java.util.List;

public interface UserDao {
    boolean usersExist();

    boolean userHasPoints(User user);

    void setResetPasswordToken(User user, String token);

    User updatePassword(User u, String password);

    void storeAuthToken(String email, String authToken);

    void deleteAuthToken(String authToken);

    User getUserByAuthToken(String authToken);

    User getUserByEmail(String email);

    void startSocketSession(User user);

    List<SocketStore> getSocketSessions(User user);

    List<User> getAllUsers();

    User getUserByIndex(int index);


}