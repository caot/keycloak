/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.testsuite.federation.ldap;

import java.util.stream.Collectors;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.storage.ldap.mappers.membership.role.RoleLDAPStorageMapperFactory;
import org.keycloak.storage.ldap.mappers.membership.role.RoleMapperConfig;
import org.keycloak.testsuite.util.LDAPRule;
import org.keycloak.testsuite.util.LDAPTestUtils;

/**
 *
 * @author rmartinc
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class LDAPRoleMapperTest extends AbstractLDAPTest {

    @ClassRule
    public static LDAPRule ldapRule = new LDAPRule();

    @Override
    protected LDAPRule getLDAPRule() {
        return ldapRule;
    }

    @Override
    protected void afterImportTestRealm() {
        testingClient.testing().ldap(TEST_REALM_NAME).prepareRolesLDAPTest();
    }

    @Test
    public void test01RoleMapperRealmRoles() {
        testingClient.server().run(session -> {
            LDAPTestContext ctx = LDAPTestContext.init(session);
            RealmModel appRealm = ctx.getRealm();

            // check users
            UserModel john = session.users().getUserByUsername("johnkeycloak", appRealm);
            Assert.assertNotNull(john);
            Assert.assertThat(john.getRealmRoleMappingsStream().map(RoleModel::getName).collect(Collectors.toSet()), Matchers.containsInAnyOrder("group1", "group2"));
            UserModel mary = session.users().getUserByUsername("marykeycloak", appRealm);
            Assert.assertNotNull(mary);
            Assert.assertThat(mary.getRealmRoleMappingsStream().map(RoleModel::getName).collect(Collectors.toSet()), Matchers.containsInAnyOrder("group1", "group2"));
            UserModel rob = session.users().getUserByUsername("robkeycloak", appRealm);
            Assert.assertNotNull(rob);
            Assert.assertThat(rob.getRealmRoleMappingsStream().map(RoleModel::getName).collect(Collectors.toSet()), Matchers.containsInAnyOrder("group1"));
            UserModel james = session.users().getUserByUsername("jameskeycloak", appRealm);
            Assert.assertNotNull(james);
            Assert.assertThat(james.getRealmRoleMappingsStream().collect(Collectors.toSet()), Matchers.empty());

            // check groups
            RoleModel group1 = appRealm.getRole("group1");
            Assert.assertNotNull(group1);
            Assert.assertThat(session.users().getRoleMembersStream(appRealm, group1).map(UserModel::getUsername).collect(Collectors.toSet()),
                    Matchers.containsInAnyOrder("johnkeycloak", "marykeycloak", "robkeycloak"));
            RoleModel group2 = appRealm.getRole("group2");
            Assert.assertNotNull(group2);
            Assert.assertThat(session.users().getRoleMembersStream(appRealm, group2).map(UserModel::getUsername).collect(Collectors.toSet()),
                    Matchers.containsInAnyOrder("johnkeycloak", "marykeycloak"));
            RoleModel group3 = appRealm.getRole("group3");
            Assert.assertNotNull(group3);
            Assert.assertThat(session.users().getRoleMembersStream(appRealm, group3).collect(Collectors.toSet()), Matchers.empty());
        });
    }

    @Test
    public void test02RoleMapperClientRoles() {
        testingClient.server().run(session -> {
            LDAPTestContext ctx = LDAPTestContext.init(session);
            RealmModel appRealm = ctx.getRealm();

            // create a client to set the roles in it
            ClientModel rolesClient = session.clients().addClient(appRealm, "role-mapper-client");

            try {
                ComponentModel mapperModel = LDAPTestUtils.getSubcomponentByName(appRealm, ctx.getLdapModel(), "rolesMapper");
                LDAPTestUtils.updateGroupMapperConfigOptions(mapperModel,
                        RoleMapperConfig.USE_REALM_ROLES_MAPPING, "false",
                        RoleMapperConfig.CLIENT_ID, rolesClient.getClientId());
                appRealm.updateComponent(mapperModel);

                // synch to the client to create the roles at the client
                new RoleLDAPStorageMapperFactory().create(session, mapperModel).syncDataFromFederationProviderToKeycloak(appRealm);

                // check users
                UserModel john = session.users().getUserByUsername("johnkeycloak", appRealm);
                Assert.assertNotNull(john);
                Assert.assertThat(john.getClientRoleMappingsStream(rolesClient).map(RoleModel::getName).collect(Collectors.toSet()), Matchers.containsInAnyOrder("group1", "group2"));
                UserModel mary = session.users().getUserByUsername("marykeycloak", appRealm);
                Assert.assertNotNull(mary);
                Assert.assertThat(mary.getClientRoleMappingsStream(rolesClient).map(RoleModel::getName).collect(Collectors.toSet()), Matchers.containsInAnyOrder("group1", "group2"));
                UserModel rob = session.users().getUserByUsername("robkeycloak", appRealm);
                Assert.assertNotNull(rob);
                Assert.assertThat(rob.getClientRoleMappingsStream(rolesClient).map(RoleModel::getName).collect(Collectors.toSet()), Matchers.containsInAnyOrder("group1"));
                UserModel james = session.users().getUserByUsername("jameskeycloak", appRealm);
                Assert.assertNotNull(james);
                Assert.assertThat(james.getClientRoleMappingsStream(rolesClient).map(RoleModel::getName).collect(Collectors.toSet()), Matchers.empty());

                // check groups
                RoleModel group1 = rolesClient.getRole("group1");
                Assert.assertNotNull(group1);
                Assert.assertThat(session.users().getRoleMembersStream(appRealm, group1).map(UserModel::getUsername).collect(Collectors.toSet()),
                        Matchers.containsInAnyOrder("johnkeycloak", "marykeycloak", "robkeycloak"));
                RoleModel group2 = rolesClient.getRole("group2");
                Assert.assertNotNull(group2);
                Assert.assertThat(session.users().getRoleMembersStream(appRealm, group2).map(UserModel::getUsername).collect(Collectors.toSet()),
                        Matchers.containsInAnyOrder("johnkeycloak", "marykeycloak"));
                RoleModel group3 = rolesClient.getRole("group3");
                Assert.assertNotNull(group3);
                Assert.assertThat(session.users().getRoleMembersStream(appRealm, group3).collect(Collectors.toSet()), Matchers.empty());

            } finally {
                appRealm.removeClient(rolesClient.getId());
            }
        });
    }
}
