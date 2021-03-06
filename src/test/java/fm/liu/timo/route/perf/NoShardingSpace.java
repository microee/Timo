/// *
// * Copyright 1999-2012 Alibaba Group.
// *
// * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
/// except
// * in compliance with the License. You may obtain a copy of the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software distributed under the
/// License
// * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
/// express
// * or implied. See the License for the specific language governing permissions and limitations
/// under
// * the License.
// */
/// **
// * (created at 2012-5-30)
// */
// package fm.liu.timo.route.perf;
//
// import java.sql.SQLNonTransientException;
//
// import fm.liu.timo.config.model.SchemaConfig;
// import fm.liu.timo.route.ServerRouter;
//
/// **
// * @author <a href="mailto:shuo.qius@alibaba-inc.com">QIU Shuo</a>
// */
// public class NoShardingSpace {
// private SchemaConfig schema;
//
// public NoShardingSpace() {
// // TimoConfig conf = TimoServer.getInstance().getConfig();
// // schema = conf.getSchemas().get("dubbo");
// }
//
// public void testDefaultSpace() throws SQLNonTransientException {
// SchemaConfig schema = this.schema;
// String stmt =
// "insert into offer (member_id, gmt_create) values ('1','2001-09-13 20:20:33')";
// for (int i = 0; i < 1000000; i++) {
// ServerRouter.route(schema, stmt, null, null);
// }
// }
//
// public static void main(String[] args) throws SQLNonTransientException {
// NoShardingSpace test = new NoShardingSpace();
// System.currentTimeMillis();
//
// long start = System.currentTimeMillis();
// test.testDefaultSpace();
// long end = System.currentTimeMillis();
// System.out.println("take " + (end - start) + " ms.");
// }
// }
