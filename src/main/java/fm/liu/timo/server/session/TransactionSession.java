package fm.liu.timo.server.session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap.KeySetView;
import fm.liu.timo.config.ErrorCode;
import fm.liu.timo.mysql.packet.ErrorPacket;
import fm.liu.timo.mysql.packet.OkPacket;
import fm.liu.timo.net.connection.BackendConnection;
import fm.liu.timo.server.ServerConnection;
import fm.liu.timo.server.session.handler.CommitHandler;
import fm.liu.timo.server.session.handler.ResultHandler;
import fm.liu.timo.server.session.handler.RollbackHandler;
import fm.liu.timo.server.session.handler.RollbackToSavepointHandler;
import fm.liu.timo.server.session.handler.SavepointHandler;

/**
 * @author liuhuanting
 */
public class TransactionSession extends AbstractSession {
    protected final String savepoint;
    protected final String rollbackToSavepoint;

    public TransactionSession(ServerConnection front) {
        super(front);
        String tag = "savepoint" + this.hashCode();
        this.savepoint = "savepoint " + tag;
        this.rollbackToSavepoint = "rollback to savepoint " + tag;
        variables.setAutocommit(false);
    }

    public String getSavepoint() {
        return savepoint;
    }

    @Override
    public void release(BackendConnection con) {}

    @Override
    public void clear() {
        front.reset();
        ArrayList<BackendConnection> rollbacks = new ArrayList<>();
        KeySetView<Integer, BackendConnection> keys = connections.keySet();
        for (Integer id : keys) {
            BackendConnection con = connections.remove(id);
            if (con.isClosed()) {
                continue;
            }
            if (con.isRunning()) {
                con.setHandler(null);
                con.close("cleared");
            } else {
                rollbacks.add(con);
            }
        }
        ResultHandler handler = new RollbackHandler();
        rollbacks.forEach(con -> con.query("rollback", handler));
    }

    @Override
    public void commit(boolean restart) {
        if (getConnections().isEmpty()) {
            super.commit(restart);
            return;
        }
        Collection<BackendConnection> cons = availableConnections();
        if (cons.size() == getConnections().size()) {
            ResultHandler handler = new CommitHandler(this, cons);
            cons.forEach(con -> con.query("commit", handler));
        } else {
            onError();
        }
    }

    @Override
    public void rollback(boolean response) {
        clear();
        if (response) {
            super.rollback(response);
        }
    }

    public void savepoint(OkPacket ok) {
        Collection<BackendConnection> cons = availableConnections();
        if (cons.size() == getConnections().size()) {
            ResultHandler handler = new SavepointHandler(this, cons.size(), ok);
            cons.forEach(con -> con.query(savepoint, handler));
        } else {
            onError();
        }
    }

    public void rollbackToSavepoint(ErrorPacket err) {
        Collection<BackendConnection> cons = availableConnections();
        if (cons.size() == getConnections().size()) {
            ResultHandler handler = new RollbackToSavepointHandler(this, cons.size(), err);
            cons.forEach(con -> con.query(rollbackToSavepoint, handler));
        } else {
            onError();
        }
    }

    protected void onError() {
        front.reset();
        this.clear();
        front.writeErrMessage(ErrorCode.ER_YES,
                "some connection already closed, transaction have been rollbacked automatically");
    }

}
