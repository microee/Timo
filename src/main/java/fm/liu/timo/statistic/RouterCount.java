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
package fm.liu.timo.statistic;

/**
 * @author xianmao.hexm 2010-9-30 上午10:43:58
 */
public final class RouterCount {

    private long routeCount;
    private long timeCount;
    private long maxRouteTime;
    private long maxRouteSQL;

    public void doRoute(long sqlId, long time) {
        routeCount++;
        timeCount += time;
        if (time > maxRouteTime) {
            maxRouteTime = time;
            maxRouteSQL = sqlId;
        }
    }

    public long getRouteCount() {
        return routeCount;
    }

    public long getTimeCount() {
        return timeCount;
    }

    public long getMaxRouteTime() {
        return maxRouteTime;
    }

    public long getMaxRouteSQL() {
        return maxRouteSQL;
    }

}
