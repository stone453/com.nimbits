/*
 * Copyright (c) 2010 Nimbits Inc.
 *
 * http://www.nimbits.com
 *
 *
 * Licensed under the GNU GENERAL PUBLIC LICENSE, Version 3.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.gnu.org/licenses/gpl.html
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the license is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, eitherexpress or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.nimbits.server.communication.xmpp;

import com.google.appengine.api.xmpp.JID;
import com.google.appengine.api.xmpp.Message;
import com.google.appengine.api.xmpp.XMPPService;
import com.google.appengine.api.xmpp.XMPPServiceFactory;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.nimbits.client.common.Utils;
import com.nimbits.client.enums.*;
import com.nimbits.client.enums.point.PointType;
import com.nimbits.client.exception.NimbitsException;
import com.nimbits.client.model.common.CommonFactoryLocator;
import com.nimbits.client.model.entity.Entity;
import com.nimbits.client.model.entity.EntityModelFactory;
import com.nimbits.client.model.entity.EntityName;
import com.nimbits.client.model.location.LocationFactory;
import com.nimbits.client.model.point.Point;
import com.nimbits.client.model.point.PointModel;
import com.nimbits.client.model.point.PointModelFactory;
import com.nimbits.client.model.user.User;
import com.nimbits.client.model.value.Value;
import com.nimbits.client.model.value.impl.ValueFactory;
import com.nimbits.server.gson.GsonFactory;
import com.nimbits.server.json.JsonHelper;
import com.nimbits.server.transactions.service.entity.EntityServiceFactory;
import com.nimbits.server.transactions.service.user.UserServiceFactory;
import com.nimbits.server.transactions.service.value.ValueServiceFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;


@SuppressWarnings("serial")
public class XMPPReceiverServlet extends HttpServlet {

    private static final Logger log = Logger.getLogger(XMPPReceiverServlet.class.getName());
    private static final Pattern COMPILE = Pattern.compile("/");
    private static final Pattern PATTERN = Pattern.compile("=");

    @Override
    public void doPost(final HttpServletRequest req, final HttpServletResponse res)
            throws IOException {
        User u = null;
        String body = null;
        try {
            final XMPPService xmpp = XMPPServiceFactory.getXMPPService();
            final Message message = xmpp.parseMessage(req);
            final JID fromJid = message.getFromJid();
            body = message.getBody();
            final String j[] = COMPILE.split(fromJid.getId());
            final String email = j[0].toLowerCase();

            log.info("XMPP Message recieved " + email + ":   " + body);
            List<Entity> result = EntityServiceFactory.getInstance().getEntityByKey(email, EntityType.user);
            if (! result.isEmpty()) {
                u =  (User) result.get(0);
                u.addAccessKey(UserServiceFactory.getServerInstance().authenticatedKey(u));

                if (body.toLowerCase().trim().equals("ls")) {
                    //sendPointList(u);
                } else if (body.indexOf('=') > 0) {

                    recordNewValue(body, u);

                } else if (!body.trim().equals("?") && !body.isEmpty() && body.charAt(body.length() - 1) == '?') {

                    sendCurrentValue(body, u);

                } else if (body.toLowerCase().startsWith("c ")) {
                    XmppServiceFactory.getInstance().sendMessage("creating point...", u.getEmail());

                    createPoint(body, u);

                } else if (body.trim().equals("?") || body.toLowerCase().equals("help")) {
                    sendHelp(u);

                } else if (JsonHelper.isJson(body)) { //it's json from the sdk
                    processJson(u, body);
                } else {
                    XmppServiceFactory.getInstance().sendMessage(":( I don't understand " + body, u.getEmail());
                }
            }
        } catch (NimbitsException e) {
            log.severe(e.getMessage());
            if (u != null) {
                try {
                    XmppServiceFactory.getInstance().sendMessage(":-o I don't understand " + body + " " + e.getMessage(), u.getEmail());
                } catch (NimbitsException e1) {
                    log.severe(e.getMessage());
                }
            }
//            if (u != null) {
//                IMFactory.getInstance().sendMessage(e.getMessage(), u.getEmail());
//            }
        }

        // ...
    }

    private static void processJson(final User u, final String body) throws NimbitsException {
        log.info(body);

        Gson gson = GsonFactory.getInstance();

        JsonParser parser = new JsonParser();
        JsonArray array = parser.parse(body).getAsJsonArray();
        Action action = gson.fromJson(array.get(0), Action.class);
        Point p = gson.fromJson(array.get(1), PointModel.class);
        log.info(body);

        switch (action) {
            case record:
                //  Point point = PointServiceFactory.getInstance().getPointByKey(p.getKey());
                Point point = (Point) EntityServiceFactory.getInstance().getEntityByKey(p.getKey(), EntityType.point).get(0);

                if (point != null) {

                    final Value v = ValueServiceFactory.getInstance().recordValue(u, point, p.getValue());
                    point.setValue(v);
                    String result = gson.toJson(point);
                    XmppServiceFactory.getInstance().sendMessage(result, u.getEmail());
                }

                break;
        }
    }

    private static void sendHelp(User u) throws NimbitsException {
        XmppServiceFactory.getInstance().sendMessage("Usage:", u.getEmail());
        XmppServiceFactory.getInstance().sendMessage("? | Help", u.getEmail());
        XmppServiceFactory.getInstance().sendMessage("c pointname | Create a data point", u.getEmail());
        XmppServiceFactory.getInstance().sendMessage("pointname? | getInstance the current value of a point", u.getEmail());
        XmppServiceFactory.getInstance().sendMessage("pointname=3.14 | record a value to that point", u.getEmail());
        XmppServiceFactory.getInstance().sendMessage("pointname=Foo Bar | record a text value to that point", u.getEmail());
    }

    private static void createPoint(final String body, final User u) throws NimbitsException {


        EntityName pointName = CommonFactoryLocator.getInstance().createName(body.substring(1).trim(), EntityType.point);
        Entity entity = EntityModelFactory.createEntity(pointName, "", EntityType.point, ProtectionLevel.everyone,
                u.getKey(), u.getKey(), UUID.randomUUID().toString());
        Point p = PointModelFactory.createPointModel(entity,0.0, 90, "", 0.0, false, false, false, 0, false, FilterType.fixedHysteresis, 0.1, false, PointType.basic, 0, false, 0.0 );

        EntityServiceFactory.getInstance().addUpdateEntity(u, p);
        //PointServiceFactory.getInstance().addPoint(u, entity);
        XmppServiceFactory.getInstance().sendMessage(pointName.getValue() + " created", u.getEmail());



    }

    private static void recordNewValue(final CharSequence body, final User u) throws NimbitsException {
        String b[] = PATTERN.split(body);
        if (b.length == 2) {

            EntityName pointName = CommonFactoryLocator.getInstance().createName(b[0], EntityType.point);
            String sval = b[1];

            try {
                double v = Double.parseDouble(sval);


                if (u != null) {
                    Value value = ValueFactory.createValueModel(LocationFactory.createLocation(), v, new Date(), "", ValueFactory.createValueData(""), AlertType.OK);
                    ValueServiceFactory.getInstance().recordValue(u, pointName, value);
                }
            } catch (NumberFormatException ignored) {

            }

        }
    }



    private static void sendCurrentValue(final String body, final User u) throws NimbitsException {
        if (!Utils.isEmptyString(body) && !body.isEmpty() && body.charAt(body.length() - 1) == '?') {
            final EntityName pointName = CommonFactoryLocator.getInstance().createName(body.replace("?", ""), EntityType.point);

            Entity e = EntityServiceFactory.getInstance().getEntityByName(u, pointName, EntityType.point).get(0);
            // Point point = PointServiceFactory.getInstance().getPointByKey(e.getKey());
            Entity point = EntityServiceFactory.getInstance().getEntityByKey(e.getKey(), EntityType.point).get(0);

            final List<Value> sample = ValueServiceFactory.getInstance().getPrevValue(point, new Date());
            if (! sample.isEmpty()) {
                Value v = sample.get(0);
                String t = "";
                if (v.getNote() != null && !v.getNote().isEmpty()) {
                    t = v.getNote();
                }
                XmppServiceFactory.getInstance().sendMessage(e.getName().getValue() + '='
                        + v.getDoubleValue() + ' ' + t, u.getEmail());
            } else {
                XmppServiceFactory.getInstance().sendMessage(pointName.getValue() + " has no data", u.getEmail());

            }
        } else {
            XmppServiceFactory.getInstance().sendMessage("I don't understand " + body, u.getEmail());

        }


    }
}