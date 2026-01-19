package hello.tradexserver.service;

import hello.tradexserver.domain.Position;

public interface PositionListener {
    void onPositionUpdate(Position position);
    void onPositionClosed(Position position);
}
