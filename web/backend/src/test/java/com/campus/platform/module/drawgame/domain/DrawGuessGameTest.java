package com.campus.platform.module.drawgame.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DrawGuessGameTest {
    private static final Instant NOW = Instant.parse("2026-09-23T09:00:00Z");

    @Test
    void roomIsCappedAndStopsAcceptingPlayersAfterStart() {
        DrawGuessGame game = new DrawGuessGame(7L, 1L, 2, 1);
        game.addPlayer(1L, "Host", null);
        game.addPlayer(2L, "Guest", null);

        assertThrows(IllegalStateException.class, () -> game.addPlayer(3L, "Late", null));
        game.start(1L, "梧桐树", NOW);
        assertThrows(IllegalStateException.class, () -> game.addPlayer(3L, "Late", null));
    }

    @Test
    void onlyHostCanStartAndOnlyCurrentDrawerCanDraw() {
        DrawGuessGame game = waitingRoom(2);
        assertThrows(IllegalStateException.class, () -> game.start(2L, "雨伞", NOW));
        game.start(1L, "雨伞", NOW);

        assertTrue(game.canDraw(1L));
        assertFalse(game.canDraw(2L));
    }

    @Test
    void answerIsOnlyVisibleToTheCurrentDrawer() {
        DrawGuessGame game = waitingRoom(2);
        game.start(1L, "樱花", NOW);

        assertEquals("樱花", game.answerFor(1L));
        assertNull(game.answerFor(2L));
        assertNull(game.answerFor(99L));
    }

    @Test
    void correctGuessScoresGuesserAndDrawerOnce() {
        DrawGuessGame game = waitingRoom(3);
        game.start(1L, "图书馆", NOW);

        DrawGuessGame.GuessResult first = game.guess(2L, " 图书馆 ", NOW.plusSeconds(8));
        DrawGuessGame.GuessResult duplicate = game.guess(2L, "图书馆", NOW.plusSeconds(9));

        assertTrue(first.correct());
        assertFalse(duplicate.accepted());
        assertEquals(Map.of(1L, 1, 2L, 1, 3L, 0), game.scores());
    }

    @Test
    void rejectsDrawingAndGuessesAtTheTurnDeadline() {
        DrawGuessGame game = waitingRoom(2);
        game.start(1L, "图书馆", NOW);

        assertTrue(game.canDraw(1L, NOW.plusSeconds(59)));
        assertFalse(game.canDraw(1L, NOW.plusSeconds(60)));
        DrawGuessGame.GuessResult expiredGuess = game.guess(2L, "图书馆", NOW.plusSeconds(60));

        assertFalse(expiredGuess.accepted());
        assertFalse(expiredGuess.correct());
        assertEquals(Map.of(1L, 0, 2L, 0), game.scores());
        assertThrows(IllegalStateException.class,
                () -> game.skip(1L, NOW.plusSeconds(60), "操场"));
    }

    @Test
    void allGuessersCorrectSchedulesFiveSecondEarlyAdvance() {
        DrawGuessGame game = waitingRoom(3);
        game.start(1L, "纸飞机", NOW);
        game.guess(2L, "纸飞机", NOW.plusSeconds(10));
        DrawGuessGame.GuessResult last = game.guess(3L, "纸飞机", NOW.plusSeconds(12));

        assertTrue(last.earlyAdvanceScheduled());
        assertEquals(NOW.plusSeconds(17), game.advanceAt());
        assertNull(game.advanceIfDue(NOW.plusSeconds(16), "操场"));
        DrawGuessGame.TurnTransition transition = game.advanceIfDue(NOW.plusSeconds(17), "操场");

        assertNotNull(transition);
        assertEquals(2L, transition.nextDrawerUserId());
        assertEquals("操场", game.answerFor(2L));
    }

    @Test
    void expiredTurnRotatesToNextPlayerAndLastTurnFinishesRoom() {
        DrawGuessGame game = waitingRoom(2);
        game.start(1L, "铅笔", NOW);
        DrawGuessGame.TurnTransition first = game.advanceIfDue(NOW.plusSeconds(60), "书包");

        assertNotNull(first);
        assertEquals(2L, first.nextDrawerUserId());
        assertEquals("PLAYING", game.status().name());

        DrawGuessGame.TurnTransition last = game.advanceIfDue(NOW.plusSeconds(120), "unused");
        assertNotNull(last);
        assertTrue(last.roomFinished());
        assertEquals("FINISHED", game.status().name());
        assertNull(game.answerFor(2L));
    }

    @Test
    void onlyDrawerCanSkipAndSkipAdvancesImmediately() {
        DrawGuessGame game = waitingRoom(2);
        game.start(1L, "书本", NOW);
        assertThrows(IllegalStateException.class, () -> game.skip(2L, NOW.plusSeconds(4), "校门"));

        DrawGuessGame.TurnTransition transition = game.skip(1L, NOW.plusSeconds(4), "校门");
        assertEquals(2L, transition.nextDrawerUserId());
    }

    @Test
    void lastOwnerCanCloseAnEmptyWaitingRoom() {
        DrawGuessGame game = new DrawGuessGame(7L, 1L, 2, 1);
        game.addPlayer(1L, "Host", null);
        game.removeWaitingPlayer(1L);

        assertEquals("FINISHED", game.status().name());
        assertEquals(0, game.playerCount());
    }

    private DrawGuessGame waitingRoom(int maxPlayers) {
        DrawGuessGame game = new DrawGuessGame(7L, 1L, maxPlayers, 1);
        game.addPlayer(1L, "Host", null);
        game.addPlayer(2L, "Guest", null);
        if (maxPlayers > 2) game.addPlayer(3L, "Third", null);
        return game;
    }
}
