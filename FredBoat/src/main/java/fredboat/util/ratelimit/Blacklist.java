/*
 * MIT License
 *
 * Copyright (c) 2017 Frederik Ar. Mikkelsen
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package fredboat.util.ratelimit;

import fredboat.db.api.BlacklistRepository;
import fredboat.db.transfer.BlacklistEntry;
import fredboat.feature.metrics.Metrics;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Created by napster on 17.04.17.
 * <p>
 * Provides a forgiving blacklist with progressively increasing blacklist lengths
 * <p>
 * In an environment where shards are running in different containers and not inside a single jar this class will need
 * some help in keeping bans up to date, that is, reading them from the database, either on changes (rethinkDB?) or
 * through an agent in regular periods
 */
public class Blacklist {

    //this holds progressively increasing lengths of blacklisting in milliseconds
    private static final List<Long> blacklistLevels;

    static {
        blacklistLevels = List.of(
                1000L * 60,                     //one minute
                1000L * 600,                    //ten minutes
                1000L * 3600,                   //one hour
                1000L * 3600 * 24,              //24 hours
                1000L * 3600 * 24 * 7           //a week
        );
    }

    private final long rateLimitHitsBeforeBlacklist;

    //users that can never be blacklisted
    private final Set<Long> userWhiteList;

    private final BlacklistRepository repository; //implementation as a RestRepo includes a cache


    public Blacklist(BlacklistRepository repository, Set<Long> userWhiteList, long rateLimitHitsBeforeBlacklist) {
        this.repository = repository;
        this.rateLimitHitsBeforeBlacklist = rateLimitHitsBeforeBlacklist;
        this.userWhiteList = Collections.unmodifiableSet(userWhiteList);
    }

    /**
     * @param id check whether this id is blacklisted
     * @return true if the id is blacklisted, false if not
     */
    //This will be called really fucking often, should be able to be accessed non-synchronized for performance
    // -> don't do any writes in here
    // -> don't call expensive methods
    public boolean isBlacklisted(long id) {

        //first of all, ppl that can never get blacklisted no matter what
        if (userWhiteList.contains(id)) return false;

        BlacklistEntry blEntry = repository.fetch(id).block();
        if (blEntry.getLevel() < 0) return false; //blacklist entry exists, but id hasn't actually been blacklisted yet


        //id was a blacklisted, but it has run out
        //noinspection RedundantIfStatement
        if (System.currentTimeMillis() > blEntry.getBlacklistTime() + (getBlacklistTimeLength(blEntry.getLevel()))) {
            return false;
        }

        //looks like this id is blacklisted ¯\_(ツ)_/¯
        return true;
    }

    /**
     * @return length if issued blacklisting, 0 if none has been issued
     */
    public long hitRateLimit(long id) {
        //update blacklist entry of this id
        long blacklistingLength = 0;
        BlacklistEntry blEntry = repository.fetch(id).block();

        //synchronize on the individual blacklist entries since we are about to change and convertAndSave them
        // we can use these to synchronize because they are backed by a cache, subsequent calls to fetch them
        // will return the same object
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (blEntry) {
            long now = System.currentTimeMillis();

            //is the last ratelimit hit a long time away (1 hour)? then reset the ratelimit hits
            if (now - blEntry.getLastHitTime() > 60 * 60 * 1000) {
                blEntry.setHitCount(0);
            }
            blEntry.incHitCount();
            blEntry.setLastHitTime(now);
            if (blEntry.getHitCount() >= rateLimitHitsBeforeBlacklist) {
                //issue blacklist incident
                blEntry.incLevel();
                if (blEntry.getLevel() < 0) blEntry.setLevel(0);
                Metrics.autoBlacklistsIssued.labels(Integer.toString(blEntry.getLevel())).inc();
                blEntry.setBlacklistTime(now);
                blEntry.setHitCount(0); //reset these for the next time

                blacklistingLength = getBlacklistTimeLength(blEntry.getLevel());
            }
            //persist it
            //if this turns up to be a performance bottleneck, have an agent run that persists the blacklist occasionally
            repository.update(blEntry);
            return blacklistingLength;
        }
    }

    /**
     * completely resets a blacklist for an id
     */
    public void liftBlacklist(long id) {
        repository.delete(id);
    }

    /**
     * Return length of a blacklist incident in milliseconds depending on the blacklist level
     */
    private long getBlacklistTimeLength(int blacklistLevel) {
        if (blacklistLevel < 0) return 0;
        return blacklistLevel >= blacklistLevels.size() ? blacklistLevels.get(blacklistLevels.size() - 1) : blacklistLevels.get(blacklistLevel);
    }
}
