/*
 * Copyright 1999-2012 Alibaba Group.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package fm.liu.timo.server.handler;

import fm.liu.timo.server.ServerConnection;
import fm.liu.timo.server.parser.ServerParse;
import fm.liu.timo.server.parser.ServerParseShow;
import fm.liu.timo.server.response.ShowDataSources;
import fm.liu.timo.server.response.ShowDatabases;
import fm.liu.timo.server.response.ShowFullTables;
import fm.liu.timo.server.response.ShowTables;
import fm.liu.timo.server.response.ShowTimoStatus;

/**
 * @author xianmao.hexm
 */
public final class ShowHandler {

    public static void handle(String stmt, ServerConnection c, int offset) {
        switch (ServerParseShow.parse(stmt, offset)) {
            case ServerParseShow.DATABASES:
                ShowDatabases.response(c);
                break;
            case ServerParseShow.DATASOURCES:
                ShowDataSources.response(c);
                break;
            case ServerParseShow.TIMO_STATUS:
                ShowTimoStatus.response(c);
                break;
            case ServerParseShow.TABLES:
                ShowTables.response(c);
                break;
            case ServerParseShow.FULL_TABLES:
                ShowFullTables.response(c);
                break;
            default:
                c.execute(stmt, ServerParse.SHOW);
        }
    }

}
