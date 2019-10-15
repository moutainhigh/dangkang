package cn.laoshini.dk.net.server;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cn.laoshini.dk.constant.GameServerProtocolEnum;
import cn.laoshini.dk.exception.BusinessException;
import cn.laoshini.dk.net.session.AbstractSession;
import cn.laoshini.dk.net.session.IMessageSender;
import cn.laoshini.dk.register.GameServerRegisterAdaptor;
import cn.laoshini.dk.server.AbstractGameServer;
import cn.laoshini.dk.util.LogUtil;
import cn.laoshini.dk.util.NetUtil;

/**
 * @author fagarine
 */
public abstract class AbstractInnerGameServer<S, M> extends AbstractGameServer {

    private final GameServerRegisterAdaptor<S, M> gameServerRegister;

    private final Map<Long, AbstractSession> innerSessionMap = new ConcurrentHashMap<>();

    private final Map<Long, S> sessionMap = new ConcurrentHashMap<>();

    public AbstractInnerGameServer(GameServerRegisterAdaptor<S, M> gameServerRegister, String serverThreadName) {
        super(gameServerRegister.toGameServerConfig(), serverThreadName);
        this.gameServerRegister = gameServerRegister;
    }

    public GameServerRegisterAdaptor<S, M> getGameServerRegister() {
        return gameServerRegister;
    }

    @Override
    public void run() {
        super.run();

        // 检查监听的端口号是否可用
        checkPort();

        // 公共依赖项检查与默认设置
        GameServerRegisterAdaptor register = getGameServerRegister();
        if (register.sessionCreator() == null) {
            // 如果用户没有设置自己的会话创建对象，默认使用系统会话
            LogUtil.debug("用户没有设置Session创建者，使用系统默认Session");
            register.setSessionCreator(s -> s);
        }

        if (register.decoder() == null) {
            throw new BusinessException("message.decoder.empty", getGameName() + "，缺失消息解码器");
        }

        if (register.encoder() == null) {
            throw new BusinessException("message.encoder.empty", getGameName() + "，缺失消息编码器");
        }

        if (register.connectOpenedOperation() == null) {
            throw new BusinessException("connect.open.invalid", getGameName() + "，缺失客户端连接成功后的处理逻辑");
        }

        if (register.messageDispatcher() == null) {
            throw new BusinessException("message.dispatcher.empty", getGameName() + "，缺失消息到达后的转发处理逻辑");
        }

        if (register.messageSender() == null) {
            boolean canEmpty = false;
            try {
                Method method = getGameServerRegister().sessionCreator().getClass()
                        .getMethod("newSession", AbstractSession.class);

                // 如果用户使用当康系统的会话类型，则允许消息发送逻辑为空
                if (AbstractSession.class.isAssignableFrom(method.getReturnType())) {
                    canEmpty = true;
                }
            } catch (NoSuchMethodException e) {
                // ignore
            }
            if (canEmpty) {
                register.onMessageSend(newDefaultSender());
            } else {
                throw new BusinessException("message.sender.empty", getGameName() + "，缺失消息发送逻辑（使用自定义会话类型的情况下不允许为空）");
            }
        }

    }

    /**
     * 返回消息发送线程池的核心线程数
     *
     * @return 正整数
     */
    protected abstract int messageSenderThreads();

    private IMessageSender newDefaultSender() {
        return IMessageSender.defaultSender(messageSenderThreads());
    }

    private void checkPort() {
        int port = getPort();
        // 端口是否已经被占用
        if (!NetUtil.localPortAble(port)) {
            throw new BusinessException("server.port.bind",
                    String.format("本地端口 [%d] 已被占用，游戏服务器 [%s] 启动失败", port, getGameName()));
        }
    }

    @Override
    public GameServerProtocolEnum getProtocolType() {
        return getGameServerRegister().protocol();
    }

    protected boolean isTcpNoDelay() {
        return GameServerProtocolEnum.TCP.equals(getProtocolType()) && getGameServerRegister().isTcpNoDelay();
    }

    protected void recordInnerSession(long sessionId, AbstractSession session) {
        innerSessionMap.put(sessionId, session);
    }

    protected AbstractSession getInnerSession(long sessionId) {
        return innerSessionMap.get(sessionId);
    }

    protected AbstractSession removeInnerSession(Long sessionId) {
        if (sessionId != null) {
            return innerSessionMap.remove(sessionId);
        }
        return null;
    }

    protected void recordSession(long sessionId, S session) {
        sessionMap.put(sessionId, session);
    }

    protected S getSession(long sessionId) {
        return sessionMap.get(sessionId);
    }

    protected S removeSession(Long sessionId) {
        if (sessionId != null) {
            return sessionMap.remove(sessionId);
        }
        return null;
    }
}