/*
 * Copyright 2015 Liu Huanting.
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
package fm.liu.timo.net.connection;

import fm.liu.timo.config.Isolations;
import fm.liu.timo.mysql.CharsetUtil;
import fm.liu.timo.util.TimeUtil;

/**
 * 连接变量
 * @author Liu Huanting 2015年5月9日
 */
public class Variables implements Cloneable {
    private volatile boolean autocommit;
    private volatile int     isolationLevel;
    private volatile int     charsetIndex;
    private volatile String  charset;
    private volatile long    upTime;
    private volatile long    lastActiveTime;
    private volatile boolean savepointChecked;

    public Variables() {
        super();
        this.setAutocommit(true);
        this.setCharset("UTF8");
        this.setIsolationLevel(Isolations.REPEATED_READ);
        this.setSavepointChecked(false);
    }

    public boolean isAutocommit() {
        return autocommit;
    }

    public void setAutocommit(boolean autocommit) {
        this.autocommit = autocommit;
    }

    public int getIsolationLevel() {
        return isolationLevel;
    }

    public void setIsolationLevel(int isolationLevel) {
        this.isolationLevel = isolationLevel;
    }

    public int getCharsetIndex() {
        return charsetIndex;
    }

    public boolean setCharsetIndex(int charsetIndex) {
        String charset = CharsetUtil.getCharset(charsetIndex);
        if (charset != null) {
            this.charset = charset;
            this.charsetIndex = charsetIndex;
            return true;
        } else {
            return false;
        }
    }

    public String getCharset() {
        return charset;
    }

    public boolean setCharset(String charset) {
        int index = CharsetUtil.getIndex(charset);
        if (index > 0) {
            this.charset = charset;
            this.charsetIndex = index;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Variables clone() {
        Variables var = new Variables();
        var.setAutocommit(this.isAutocommit());
        var.setCharsetIndex(this.getCharsetIndex());
        var.setIsolationLevel(this.getIsolationLevel());
        return var;
    }

    public long getUpTime() {
        return upTime;
    }

    public void setUpTime(long upTime) {
        this.upTime = upTime;
    }

    public long getLastActiveTime() {
        return lastActiveTime;
    }

    public void update() {
        this.lastActiveTime = TimeUtil.currentTimeMillis();
    }

    public boolean isSavepointChecked() {
        return savepointChecked;
    }

    public void setSavepointChecked(boolean savepointChecked) {
        this.savepointChecked = savepointChecked;
    }
}
