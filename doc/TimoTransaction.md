# Timo Transaction

## Timo的事务模型

在分布式数据库中，2PC带来的性能问题比较严峻，因此Timo目前采用了`最大努力一次提交`的事务模型。

Timo将会对事务中的每一个请求实时下发到后端相应的数据节点中，在前端发出`commit`语句之前，所有的操作都是可以回滚的。在前端发出`commit`语句之时，Timo将检测后端数据节点的可用性，如果后端节点出现异常，Timo将回滚所有节点，并通知前端事务失败；如果没有异常，Timo将向所有后端发送`commit`命令。

在Timo将`commit`指令发出之后，收到MySQL应答之前，如果基础设施出现问题（网络中断，宕机等），则可能会出现不一致的情况。考虑到这个概率比较小，相对于它带来的性能优势而言并非不可容忍，因此Timo采用了该模型来实现弱一致性的分布式事务。

## Timo的事务实现

Timo中事务操作的流程图如下：
![image] (TimoTransaction.png)

其中：
- 通过`start transaction`,`begin`或者`set autocommit=0`来开启一个事务
- 通过`commit`或者`rollback`或者会引起隐式提交的语句来结束一个事务
- 为了保证分布式事务中单条SQL在拆分成多个子事务时不带来原子性问题，Timo将在每一个全节点成功的`IUD`操作后进行`savepoint`操作，以便失败时可以回滚
- 前端连接断开的情形会导致后端事务回滚
- 所有后端连接断开的情形都会导致事务强制回滚
- 事务中后端连接`autocommit`都是为`0`的，前端连接中该变量由Timo维护
- 事务隔离级别的设置仅对`SESSION`级别有效

## 后续可能的改进

- 更激进的做法是，去除`savepoint`操作，允许不一致或者强制要求回滚
- 更保守的做法是，支持MySQL的XA事务

## 需要注意的事情

- 一个会话中后端多个数据节点不保证同时开启事务
- 批量插入等针对后端多个数据节点的操作，为了保证该操作的原子性，需要走事务模型
- MySQL的XA事务在5.7.7版本以后才算勉强可用，提供支持时需要注意
