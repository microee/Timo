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
package fm.liu.timo.mysql.connection;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.pmw.tinylog.Logger;
import fm.liu.timo.backend.Source;
import fm.liu.timo.config.Capabilities;
import fm.liu.timo.config.ErrorCode;
import fm.liu.timo.mysql.ByteUtil;
import fm.liu.timo.mysql.SecurityUtil;
import fm.liu.timo.mysql.Sync;
import fm.liu.timo.mysql.handler.AuthenticatorHandler;
import fm.liu.timo.mysql.packet.AuthPacket;
import fm.liu.timo.mysql.packet.CommandPacket;
import fm.liu.timo.mysql.packet.EOFPacket;
import fm.liu.timo.mysql.packet.ErrorPacket;
import fm.liu.timo.mysql.packet.HandshakePacket;
import fm.liu.timo.mysql.packet.MySQLPacket;
import fm.liu.timo.mysql.packet.OkPacket;
import fm.liu.timo.net.NIOProcessor;
import fm.liu.timo.net.connection.BackendConnection;
import fm.liu.timo.net.connection.Variables;
import fm.liu.timo.server.session.Session;
import fm.liu.timo.server.session.TransactionSession;
import fm.liu.timo.server.session.handler.ResultHandler;
import fm.liu.timo.server.session.handler.SessionResultHandler;
import fm.liu.timo.server.session.handler.SyncHandler;

/**
 * @author Liu Huanting 2015年5月9日
 */
public class MySQLConnection extends BackendConnection {
    private static final long CLIENT_FLAGS = initClientFlags();

    private static long initClientFlags() {
        int flag = 0;
        flag |= Capabilities.CLIENT_LONG_PASSWORD;
        flag |= Capabilities.CLIENT_FOUND_ROWS;
        flag |= Capabilities.CLIENT_LONG_FLAG;
        flag |= Capabilities.CLIENT_CONNECT_WITH_DB;
        // flag |= Capabilities.CLIENT_NO_SCHEMA;
        // flag |= Capabilities.CLIENT_COMPRESS;
        flag |= Capabilities.CLIENT_ODBC;
        // flag |= Capabilities.CLIENT_LOCAL_FILES;
        flag |= Capabilities.CLIENT_IGNORE_SPACE;
        flag |= Capabilities.CLIENT_PROTOCOL_41;
        flag |= Capabilities.CLIENT_INTERACTIVE;
        // flag |= Capabilities.CLIENT_SSL;
        flag |= Capabilities.CLIENT_IGNORE_SIGPIPE;
        flag |= Capabilities.CLIENT_TRANSACTIONS;
        // flag |= Capabilities.CLIENT_RESERVED;
        flag |= Capabilities.CLIENT_SECURE_CONNECTION;
        // client extension
        // flag |= Capabilities.CLIENT_MULTI_STATEMENTS;
        flag |= Capabilities.CLIENT_MULTI_RESULTS;
        return flag;
    }

    private Source                   datasource;
    private long                     threadID;
    private final int                datanodeID;
    private HandshakePacket          handshake;
    private long                     clientFlags;
    private boolean                  isAuthenticated;
    private String                   username;
    private String                   password;
    private volatile String          db = "";
    protected volatile ResultHandler resultHandler;

    public MySQLConnection(SocketChannel channel, NIOProcessor processor, int datanodeID) {
        super(channel, processor);
        this.datanodeID = datanodeID;
        this.clientFlags = CLIENT_FLAGS;
    }

    public final void onRead(int got) {
        if (isClosed()) {
            return;
        }
        if (resultHandler != null) {
            decode();
        } else {
            if (!isAuthenticated) {
                super.onRead(got);
            } else {
                this.close("no handler to deal with the data");
            }
        }
    }

    private void decode() {
        ByteBuffer buffer = this.readBuffer;
        int offset = 0, position = buffer.position(), length;
        for (;;) {
            length = MySQLPacket.getPacketLength(buffer, offset);
            if (length == -1) {
                break;
            }
            if (position >= offset + length) {
                byte[] data = new byte[length];

                buffer.position(offset);
                buffer.get(data, 0, length);
                buffer.position(position);
                dispose(data);
                offset += length;
                continue;
            } else {
                break;
            }
        }
        if (offset < position) {
            buffer.position(position);
            readBuffer = checkBuffer(buffer, offset, length);
        } else {
            buffer.clear();
        }
    }

    public void auth() {
        AuthPacket packet = new AuthPacket();
        packet.packetId = 1;
        packet.clientFlags = clientFlags;
        packet.maxPacketSize = MySQLPacket.MAX_PACKET_SIZE;
        packet.charsetIndex = variables.getCharsetIndex();
        packet.user = username;
        try {
            packet.password = passwd(password, handshake);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e.getMessage());
        }
        packet.database = db;
        packet.write(this);
    }

    private static byte[] passwd(String pass, HandshakePacket hs) throws NoSuchAlgorithmException {
        if (pass == null || pass.length() == 0) {
            return null;
        }
        byte[] passwd = pass.getBytes();
        int sl1 = hs.seed.length;
        int sl2 = hs.restOfScrambleBuff.length;
        byte[] seed = new byte[sl1 + sl2];
        System.arraycopy(hs.seed, 0, seed, 0, sl1);
        System.arraycopy(hs.restOfScrambleBuff, 0, seed, sl1, sl2);
        return SecurityUtil.scramble411(passwd, seed);
    }

    private volatile int          resultStatus;
    private volatile byte[]       header;
    private volatile List<byte[]> fields;

    public class ResponseStatus {
        public static final int RESULT_STATUS_INIT      = 0;
        public static final int RESULT_STATUS_HEADER    = 1;
        public static final int RESULT_STATUS_FIELD_EOF = 2;
        public static final int RESULT_STATUS_END       = 3;
    }

    private void dispose(byte[] data) {
        byte reponsePacketType = data[4];
        switch (resultStatus) {
            case ResponseStatus.RESULT_STATUS_INIT:
                switch (reponsePacketType) {
                    case OkPacket.FIELD_COUNT:
                        this.setState(State.borrowed);
                        resultHandler.ok(data, this);
                        break;
                    case ErrorPacket.FIELD_COUNT:
                        this.setState(State.borrowed);
                        resultHandler.error(data, this);
                        break;
                    default:
                        resultStatus = ResponseStatus.RESULT_STATUS_HEADER;
                        header = data;
                        fields = new ArrayList<byte[]>((int) ByteUtil.readLength(data, 4));
                }
                break;
            case ResponseStatus.RESULT_STATUS_HEADER:
                switch (reponsePacketType) {
                    case ErrorPacket.FIELD_COUNT:
                        resultStatus = ResponseStatus.RESULT_STATUS_INIT;
                        this.setState(State.borrowed);
                        resultHandler.error(data, this);
                        break;
                    case EOFPacket.FIELD_COUNT:
                        resultStatus = ResponseStatus.RESULT_STATUS_FIELD_EOF;
                        resultHandler.field(header, fields, data, this);
                        break;
                    default:
                        fields.add(data);
                }
                break;
            case ResponseStatus.RESULT_STATUS_FIELD_EOF:
                switch (reponsePacketType) {
                    case ErrorPacket.FIELD_COUNT:
                        resultStatus = ResponseStatus.RESULT_STATUS_INIT;
                        this.setState(State.borrowed);
                        resultHandler.error(data, this);
                        break;
                    case EOFPacket.FIELD_COUNT:
                        resultStatus = ResponseStatus.RESULT_STATUS_INIT;
                        this.setState(State.borrowed);
                        resultHandler.eof(data, this);
                        break;
                    default:
                        resultHandler.row(data, this);
                }
                break;
            default:
                throw new RuntimeException("unknown status when process MySQL Packet!");
        }
    }

    public void setState(int state) {
        this.state = state;
    }

    public int getState() {
        return state;
    }

    private ByteBuffer checkBuffer(ByteBuffer buffer, int offset, int length) {
        if (length > buffer.capacity()) {
            ByteBuffer newBuffer = this.processor.getBufferPool().allocate(length);
            buffer.limit(buffer.position());
            buffer.position(offset);
            newBuffer.put(buffer);
            return newBuffer;
        }
        if (offset > 0) {
            buffer.limit(buffer.position());
            buffer.position(offset);
            buffer.compact();
        }
        return buffer;
    }

    @Override
    public void error(int errCode, Throwable t) {
        switch (errCode) {
            case ErrorCode.ERR_HANDLE_DATA:
                break;
            case ErrorCode.ERR_PUT_WRITE_QUEUE:
                break;
            case ErrorCode.ERR_CONNECT_SOCKET:
                if (handler == null) {
                    return;
                }
                if (resultHandler != null) {
                    final ResultHandler temp = resultHandler;
                    resultHandler = null;
                    temp.close("connectionError");
                } else if (handler instanceof AuthenticatorHandler) {
                    AuthenticatorHandler theHandler = (AuthenticatorHandler) handler;
                    theHandler.error(t);
                }
                break;
        }
    }

    @Override
    public void close(String reason) {
        if (resultHandler != null) {
            // 由线程池去执行关闭后的操作
            final ResultHandler handler = resultHandler;
            resultHandler = null;
            this.processor.getExecutor().execute(() -> handler.close(null));
        }
        if (!super.closed.compareAndSet(false, true)) {
            return;
        }

        // 如果已经认证过了,并且没有在执行SQL，发送quit命令
        if (isAuthenticated && !this.isRunning()) {
            write(writeToBuffer(CommandPacket.QUIT, allocate()));
            write(allocate());
        }
        datasource.remove(this);
        processor.remove(this);
        super.cleanup();// 立即关闭物理socket
        Logger.debug("connection {} closed due to {}", this, reason);
    }

    @Override
    public void handle(byte[] data) {
        handler.handle(data);
    }

    @Override
    public void onConnectFailed(Throwable e) {
        this.error(ErrorCode.ERR_CONNECT_SOCKET, e);
    }

    @Override
    public void query(String sql, ResultHandler handler) {
        ResultHandler _handler = handler;
        if (handler instanceof SessionResultHandler) {
            Session session = ((SessionResultHandler) handler).session;
            Variables var = session.getVariables();
            String sync = null;
            if (session instanceof TransactionSession) {
                sync = variables.isSavepointChecked() ? null
                        : ((TransactionSession) session).getSavepoint();
                if (sync != null) {
                    _handler = new SyncHandler(_handler, sql,
                            () -> variables.setSavepointChecked(true));
                    sql = sync;
                }
            }
            final int charsetIndex = var.getCharsetIndex();
            sync = variables.getCharsetIndex() != charsetIndex
                    ? Sync.getCharsetCommandStr(charsetIndex) : null;
            if (sync != null) {
                _handler = new SyncHandler(_handler, sql,
                        () -> variables.setCharsetIndex(charsetIndex));
                sql = sync;
            }
            final int isolationLevel = var.getIsolationLevel();
            sync = variables.getIsolationLevel() != isolationLevel
                    ? Sync.getTxIsolationCommandStr(isolationLevel) : null;
            if (sync != null) {
                _handler = new SyncHandler(_handler, sql,
                        () -> variables.setIsolationLevel(isolationLevel));
                sql = sync;
            }
            final boolean autocommit = var.isAutocommit();
            sync = variables.isAutocommit() != autocommit ? Sync.getAutoCommitCommandStr(autocommit)
                    : null;
            if (sync != null) {
                _handler =
                        new SyncHandler(_handler, sql, () -> variables.setAutocommit(autocommit));
                sql = sync;
            }
        }
        if (this.isClosed()) {
            this.setResultHandler(null);
            handler.close("backend connection already closed!");
            return;
        }
        this.setResultHandler(_handler);
        this.setState(State.running);
        CommandPacket packet = new CommandPacket(CommandPacket.COM_QUERY);
        packet.arg = sql.getBytes();
        packet.write(this);
        variables.update();
    }

    public void setResultHandler(ResultHandler handler) {
        this.resultHandler = handler;
    }

    public ResultHandler getResultHandler() {
        return resultHandler;
    }

    @Override
    public void release() {
        resultHandler = null;
        datasource.release(this);
    }

    public void setAuthenticated(boolean isAuthenticated) {
        this.isAuthenticated = isAuthenticated;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public Source getDatasource() {
        return datasource;
    }

    public void setDatasource(Source datasource) {
        this.datasource = datasource;
    }

    public HandshakePacket getHandshake() {
        return handshake;
    }

    public void setHandshake(HandshakePacket handshake) {
        this.handshake = handshake;
    }

    @Override
    public long getThreadID() {
        return threadID;
    }

    public void setThreadID(long id) {
        this.threadID = id;
    }

    public int getDatanodeID() {
        return datanodeID;
    }

}
