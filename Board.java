/* Skeleton Copyright (C) 2015, 2020 Paul N. Hilfinger and the Regents of the
 * University of California.  All rights reserved. */
package loa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;

import java.util.regex.Pattern;

import static loa.Piece.*;
import static loa.Square.*;
import static loa.Move.*;

/** Represents the state of a game of Lines of Action.
 *  @author Ishaan Mauli Mishra
 */
class Board {

    /** Default number of moves for each side that results in a draw. */
    static final int DEFAULT_MOVE_LIMIT = 60;

    /** Pattern describing a valid square designator (cr). */
    static final Pattern ROW_COL = Pattern.compile("^[a-h][1-8]$");

    /** A Board whose initial contents are taken from INITIALCONTENTS
     *  and in which the player playing TURN is to move. The resulting
     *  Board has
     *        get(col, row) == INITIALCONTENTS[row][col]
     *  Assumes that PLAYER is not null and INITIALCONTENTS is 8x8.
     *
     *  CAUTION: The natural written notation for arrays initializers puts
     *  the BOTTOM row of INITIALCONTENTS at the top.
     */
    Board(Piece[][] initialContents, Piece turn) {
        initialize(initialContents, turn);
    }

    /** A new board in the standard initial position. */
    Board() {
        this(INITIAL_PIECES, BP);
    }

    /** A Board whose initial contents and state are copied from
     *  BOARD. */
    Board(Board board) {
        this();
        copyFrom(board);
    }

    /** Set my state to CONTENTS with SIDE to move. */
    void initialize(Piece[][] contents, Piece side) {
        for (int i = 0; i < contents.length; i += 1) {
            for (int j = 0; j < contents[i].length; j += 1) {
                _board[sq(i, j).index()] = contents[j][i];
            }
        }
        _turn = side;
        _moveLimit = DEFAULT_MOVE_LIMIT;
        _winnerKnown = false;
        _winner = null;
        _moves.clear();
        _subsetsInitialized = false;
        computeRegions();
    }

    /** Set me to the initial configuration. */
    void clear() {
        initialize(INITIAL_PIECES, BP);
    }

    /** Set my state to a copy of BOARD. */
    void copyFrom(Board board) {
        if (board == this) {
            return;
        }
        System.arraycopy(board._board, 0, _board, 0, _board.length);
        _moves.clear();
        _moves.addAll(board._moves);
        _turn = board._turn;
        _moveLimit = board._moveLimit;
        _winnerKnown = board._winnerKnown;
    }

    /** Return the contents of the square at SQ. */
    Piece get(Square sq) {
        return _board[sq.index()];
    }

    /** Set the square at SQ to V and set the side that is to move next
     *  to NEXT, if NEXT is not null. */
    void set(Square sq, Piece v, Piece next) {
        _board[sq.index()] = v;
        if (next != null) {
            _turn = next;
        }
    }

    /** Set the square at SQ to V, without modifying the side that
     *  moves next. */
    void set(Square sq, Piece v) {
        set(sq, v, null);
    }

    /** Set limit on number of moves by each side that results in a tie to
     *  LIMIT, where 2 * LIMIT > movesMade(). */
    void setMoveLimit(int limit) {
        if (2 * limit <= movesMade()) {
            throw new IllegalArgumentException("move limit too small");
        }
        _moveLimit = 2 * limit;
    }

    /** Returns the move limit.
     * @return move limit*/
    int getMoveLimit() {
        return _moveLimit;
    }

    /** Assuming isLegal(MOVE), make MOVE. This function assumes that
     *  MOVE.isCapture() will return false.  If it saves the move for
     *  later retraction, makeMove itself uses MOVE.captureMove() to produce
     *  the capturing move. */
    void makeMove(Move move) {
        assert isLegal(move);
        if (get(move.getTo()) == _turn.opposite()) {
            move = move.captureMove();
        }
        set(move.getTo(), get(move.getFrom()));
        set(move.getFrom(), EMP, turn().opposite());
        _moves.add(move);
        _subsetsInitialized = false;
    }

    /** Retract (unmake) one move, returning to the state immediately before
     *  that move.  Requires that movesMade () > 0. */
    void retract() {
        assert movesMade() > 0;
        Move lastMove = _moves.remove(_moves.size() - 1);
        set(lastMove.getFrom(), get(lastMove.getTo()));
        if (lastMove.isCapture()) {
            set(lastMove.getTo(), get(lastMove.getFrom()).opposite(),
                    turn().opposite());
        } else {
            set(lastMove.getTo(), EMP, turn().opposite());
        }
        _subsetsInitialized = false;
        _winnerKnown = false;
    }

    /** Return the Piece representing who is next to move. */
    Piece turn() {
        return _turn;
    }

    /** Return true iff FROM - TO is a legal move for the player currently on
     *  move. */
    boolean isLegal(Square from, Square to) {
        if (get(from) == turn() && from.isValidMove(to) && !blocked(from, to)) {
            int dir = from.direction(to);
            int numPieces = countPieces(from, dir);
            return numPieces == from.distance(to);
        }
        return false;
    }

    /** Returns the number of pieces along the line in the direction DIR of
     * Square SQ.
     * @param sq the Square along whose line we count.
     * @param dir the direction along which we count.
     * @return number of pieces. */
    int countPieces(Square sq, int dir) {
        int oppDir = (dir + 4) % 8;
        int numPieces = 1;
        for (int i = 1; i < 8; i += 1) {
            Square other = sq.moveDest(dir, i);
            if (other == null) {
                break;
            }
            if (get(other) != EMP) {
                numPieces += 1;
            }
        }
        for (int i = 1; i < 8; i += 1) {
            Square other = sq.moveDest(oppDir, i);
            if (other == null) {
                break;
            }
            if (get(other) != EMP) {
                numPieces += 1;
            }
        }
        return numPieces;
    }

    /** Return true iff MOVE is legal for the player currently on move.
     *  The isCapture() property is ignored. */
    boolean isLegal(Move move) {
        return isLegal(move.getFrom(), move.getTo());
    }

    /** Return a sequence of all legal moves from this position. */
    List<Move> legalMoves() {
        List<Move> legalMoves = new ArrayList<>();
        for (Square from : ALL_SQUARES) {
            if (get(from) == turn()) {
                for (int dir = 0; dir < 8; dir += 1) {
                    for (Square to = from.moveDest(dir, 1); to != null;
                         to = to.moveDest(dir, 1)) {
                        Move move = mv(from, to);
                        if (isLegal(move)) {
                            legalMoves.add(move);
                        }
                    }
                }
            }
        }
        return legalMoves;
    }

    /** Return true iff the game is over (either player has all his
     *  pieces contiguous or there is a tie). */
    boolean gameOver() {
        return winner() != null;
    }

    /** Return true iff SIDE's pieces are contiguous. */
    boolean piecesContiguous(Piece side) {
        return getRegionSizes(side).size() == 1;
    }

    /** Return the winning side, if any.  If the game is not over, result is
     *  null.  If the game has ended in a tie, returns EMP. */
    Piece winner() {
        if (!_winnerKnown) {
            if (piecesContiguous(turn().opposite())) {
                _winner = turn().opposite();
            } else if (piecesContiguous(turn())) {
                _winner = turn();
            } else if (movesMade() == _moveLimit) {
                _winner = EMP;
            } else {
                return null;
            }
            _winnerKnown = true;
        }
        return _winner;
    }

    /** Return the total number of moves that have been made (and not
     *  retracted).  Each valid call to makeMove with a normal move increases
     *  this number by 1. */
    int movesMade() {
        return _moves.size();
    }

    @Override
    public boolean equals(Object obj) {
        Board b = (Board) obj;
        return Arrays.deepEquals(_board, b._board) && _turn == b._turn;
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(_board) * 2 + _turn.hashCode();
    }

    @Override
    public String toString() {
        Formatter out = new Formatter();
        out.format("===%n");
        for (int r = BOARD_SIZE - 1; r >= 0; r -= 1) {
            out.format("    ");
            for (int c = 0; c < BOARD_SIZE; c += 1) {
                out.format("%s ", get(sq(c, r)).abbrev());
            }
            out.format("%n");
        }
        out.format("Next move: %s%n===", turn().fullName());
        return out.toString();
    }

    /** Return true if a move from FROM to TO is blocked by an opposing
     *  piece or by a friendly piece on the target square. */
    private boolean blocked(Square from, Square to) {
        if (get(to) == turn()) {
            return true;
        }
        int dir = from.direction(to);
        for (Square next = from.moveDest(dir, 1); next != to;
             next = next.moveDest(dir, 1)) {
            if (get(next) == turn().opposite()) {
                return true;
            }
        }
        return false;
    }

    /** Return the size of the as-yet unvisited cluster of squares
     *  containing P at and adjacent to SQ.  VISITED indicates squares that
     *  have already been processed or are in different clusters.  Update
     *  VISITED to reflect squares counted. */
    private int numContig(Square sq, boolean[][] visited, Piece p) {
        if (p == EMP || get(sq) != p || visited[sq.col()][sq.row()]) {
            return 0;
        }
        visited[sq.col()][sq.row()] = true;
        int num = 1;
        for (Square adjacent : sq.adjacent()) {
            num += numContig(adjacent, visited, p);
        }
        return num;
    }

    /** Set the values of _whiteRegionSizes and _blackRegionSizes. */
    private void computeRegions() {
        if (_subsetsInitialized) {
            return;
        }
        _whiteRegionSizes.clear();
        _blackRegionSizes.clear();
        boolean[][] vistitedWhite = new boolean[BOARD_SIZE][BOARD_SIZE];
        boolean[][] vistitedBlack = new boolean[BOARD_SIZE][BOARD_SIZE];
        for (Square sq : ALL_SQUARES) {
            if (get(sq) == WP) {
                int contiguousWhite = numContig(sq, vistitedWhite, WP);
                if (contiguousWhite > 0) {
                    _whiteRegionSizes.add(contiguousWhite);
                }
            } else if (get(sq) == BP) {
                int contiguousBlack = numContig(sq, vistitedBlack, BP);
                if (contiguousBlack > 0) {
                    _blackRegionSizes.add(contiguousBlack);
                }
            }
        }
        Collections.sort(_whiteRegionSizes, Collections.reverseOrder());
        Collections.sort(_blackRegionSizes, Collections.reverseOrder());
        _subsetsInitialized = true;
    }

    /** Return the sizes of all the regions in the current union-find
     *  structure for side S. */
    List<Integer> getRegionSizes(Piece s) {
        computeRegions();
        if (s == WP) {
            return _whiteRegionSizes;
        } else {
            return _blackRegionSizes;
        }
    }

    /** Undoes the previous two moves. */
    void undo() {
        if (movesMade()  > 1 && !gameOver()) {
            retract();
            retract();
        }
    }

    /** Returns number of pieces of P on this board.
     * @param p Piece object to check for.
     * @return number of pieces of P on me. */
    int numPieces(Piece p) {
        int num = 0;
        for (int i = 0; i < getRegionSizes(p).size(); i += 1) {
            num += getRegionSizes(p).get(i);
        }
        return num;
    }

    /** Returns a heuristic estimate of the board. A larger (more positive)
     * value indicates a better position for the white piece and a smaller
     * (more negative) value indicates a better position for the black piece.
     * @return numerical heuristic estimate of the board. */
    int heuristicEstimate() {
        int diff = getRegionSizes(BP).size() - getRegionSizes(WP).size();
        double whiteRatio = (double) getRegionSizes(WP).get(0) / numPieces(WP);
        double blackRatio = (double) getRegionSizes(BP).get(0) / numPieces(BP);
        return (int) Math.sqrt(Integer.MAX_VALUE) * diff * (int) (diff > 0
                ? whiteRatio / blackRatio : blackRatio / whiteRatio);
    }

    /** The standard initial configuration for Lines of Action (bottom row
     *  first). */
    static final Piece[][] INITIAL_PIECES = {
        { EMP, BP,  BP,  BP,  BP,  BP,  BP,  EMP },
        { WP,  EMP, EMP, EMP, EMP, EMP, EMP, WP  },
        { WP,  EMP, EMP, EMP, EMP, EMP, EMP, WP  },
        { WP,  EMP, EMP, EMP, EMP, EMP, EMP, WP  },
        { WP,  EMP, EMP, EMP, EMP, EMP, EMP, WP  },
        { WP,  EMP, EMP, EMP, EMP, EMP, EMP, WP  },
        { WP,  EMP, EMP, EMP, EMP, EMP, EMP, WP  },
        { EMP, BP,  BP,  BP,  BP,  BP,  BP,  EMP }
    };

    /** Current contents of the board.  Square S is at _board[S.index()]. */
    private final Piece[] _board = new Piece[BOARD_SIZE * BOARD_SIZE];

    /** List of all unretracted moves on this board, in order. */
    private final ArrayList<Move> _moves = new ArrayList<>();
    /** Current side on move. */
    private Piece _turn;
    /** Limit on number of moves before tie is declared. */
    private int _moveLimit;
    /** True iff the value of _winner is known to be valid. */
    private boolean _winnerKnown;
    /** Cached value of the winner (BP, WP, EMP (for tie), or null (game still
     *  in progress). Use only if _winnerKnown. */
    private Piece _winner;

    /** True iff subsets computation is up-to-date. */
    private boolean _subsetsInitialized;

    /** List of the sizes of continguous clusters of pieces, by color. */
    private final ArrayList<Integer>
        _whiteRegionSizes = new ArrayList<>(),
        _blackRegionSizes = new ArrayList<>();
}
