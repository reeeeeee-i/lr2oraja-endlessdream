package bms.player.beatoraja.rivals;

import bms.player.beatoraja.ScoreData;
import bms.player.beatoraja.ScoreDatabaseAccessor;
import bms.player.beatoraja.select.ScoreDataCache;
import bms.player.beatoraja.song.SongData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link ScoreDataCache} intended for use with rivals. Adds methods to operate on underlying
 * database.
 */
public final class RivalDataCache extends ScoreDataCache {

	private static final Logger logger = LoggerFactory.getLogger(RivalDataCache.class);

    private final ScoreDatabaseAccessor scoreDb;
    private final String playerName; // ライバルプレイヤー名

    public RivalDataCache(ScoreDatabaseAccessor scoreDb) {
        this.scoreDb = scoreDb;
        bms.player.beatoraja.PlayerInformation info = scoreDb.getInformation();
        this.playerName = info != null ? info.getName() : "Rival";
    }

    @Override
    protected ScoreData readScoreDatasFromSource(SongData song, int lnmode) {
        ScoreData score = scoreDb.getScoreData(song.getSha256(), song.hasUndefinedLongNote() ? lnmode : 0);
        if (score != null) {
            score.setPlayer(this.playerName);
        }
        return score;
    }

    @Override
    protected void readScoreDatasFromSource(
            ScoreDatabaseAccessor.ScoreDataCollector collector,
            SongData[] songs,
            int lnmode
    ) {
        logger.error("Unimplemented: this method is never called from rivals context");
    }

    public void updateScore(ScoreData scoreData, SongData songData, int lnMode) {
        scoreDb.setScoreData(scoreData);
        this.update(songData, lnMode);
    }

    public void updateAllScores(ScoreData[] scores) {
        scoreDb.setScoreData(scores);
        this.clear();
    }
}
