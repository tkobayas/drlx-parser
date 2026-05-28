package org.drools.drlx.builder;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.drools.base.time.TimeUtils;
import org.drools.model.functions.temporal.*;

public final class TemporalPredicateFactory {

    private static final Set<String> THRESHOLD_ONLY = Set.of(
            "finishes", "finishedby", "meets", "metby", "starts", "startedby");

    private TemporalPredicateFactory() {}

    public static TemporalPredicate create(String operator, boolean negated, List<String> params) {
        if (THRESHOLD_ONLY.contains(operator) && params.size() > 1) {
            throw new IllegalArgumentException(
                    "Operator '" + operator + "' accepts 0 or 1 parameters, but "
                    + params.size() + " were given");
        }

        TemporalPredicate pred = switch (operator) {
            case "after"        -> createRange(params, AfterPredicate::new,
                                       AfterPredicate::new, AfterPredicate::new);
            case "before"       -> createRange(params, BeforePredicate::new,
                                       BeforePredicate::new, BeforePredicate::new);
            case "coincides"    -> createRange(params,
                                       () -> new CoincidesPredicate(0, TimeUnit.MILLISECONDS),
                                       CoincidesPredicate::new, CoincidesPredicate::new);
            case "during"       -> createRange(params, DuringPredicate::new,
                                       DuringPredicate::new, DuringPredicate::new);
            case "finishes"     -> createThreshold(params, FinishesPredicate::new,
                                       FinishesPredicate::new);
            case "finishedby"   -> createThreshold(params, FinishedbyPredicate::new,
                                       FinishedbyPredicate::new);
            case "includes"     -> createRange(params, IncludesPredicate::new,
                                       IncludesPredicate::new, IncludesPredicate::new);
            case "meets"        -> createThreshold(params, MeetsPredicate::new,
                                       MeetsPredicate::new);
            case "metby"        -> createThreshold(params, MetbyPredicate::new,
                                       MetbyPredicate::new);
            case "overlaps"     -> createRange(params, OverlapsPredicate::new,
                                       OverlapsPredicate::new, OverlapsPredicate::new);
            case "overlappedby" -> createRange(params, OverlappedbyPredicate::new,
                                       OverlappedbyPredicate::new, OverlappedbyPredicate::new);
            case "starts"       -> createThreshold(params, StartsPredicate::new,
                                       StartsPredicate::new);
            case "startedby"    -> createThreshold(params, StartedbyPredicate::new,
                                       StartedbyPredicate::new);
            default -> throw new IllegalArgumentException(
                           "Unknown temporal operator: " + operator);
        };
        return negated ? pred.negate() : pred;
    }

    @FunctionalInterface
    interface NoArgFactory { TemporalPredicate create(); }
    @FunctionalInterface
    interface OneArgFactory { TemporalPredicate create(long v, TimeUnit u); }
    @FunctionalInterface
    interface TwoArgFactory { TemporalPredicate create(long v1, TimeUnit u1, long v2, TimeUnit u2); }

    private static TemporalPredicate createRange(List<String> params,
                                                  NoArgFactory f0,
                                                  OneArgFactory f1,
                                                  TwoArgFactory f2) {
        return switch (params.size()) {
            case 0 -> f0.create();
            case 1 -> f1.create(parseMs(params.get(0)), TimeUnit.MILLISECONDS);
            default -> f2.create(parseMs(params.get(0)), TimeUnit.MILLISECONDS,
                                 parseMs(params.get(1)), TimeUnit.MILLISECONDS);
        };
    }

    private static TemporalPredicate createThreshold(List<String> params,
                                                      NoArgFactory f0,
                                                      OneArgFactory f1) {
        return switch (params.size()) {
            case 0 -> f0.create();
            default -> f1.create(parseMs(params.get(0)), TimeUnit.MILLISECONDS);
        };
    }

    private static long parseMs(String duration) {
        return TimeUtils.parseTimeString(duration);
    }
}
