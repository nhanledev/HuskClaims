/*
 * This file is part of HuskClaims, licensed under the Apache License 2.0.
 *
 *  Copyright (c) William278 <will27528@gmail.com>
 *  Copyright (c) contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.william278.huskclaims.command;

import com.google.common.collect.Lists;
import net.jodah.expiringmap.ExpiringMap;
import net.william278.huskclaims.HuskClaims;
import net.william278.huskclaims.config.Locales;
import net.william278.huskclaims.user.CommandUser;
import net.william278.huskclaims.user.OnlineUser;
import net.william278.huskclaims.user.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class UserClaimsListCommand extends ClaimsListCommand implements UserListTabCompletable {

    private final ExpiringMap<UUID, List<ServerWorldClaim>> claimLists = ExpiringMap.builder()
            .expiration(LIST_CACHE_MINUTES, TimeUnit.MINUTES).build();

    protected UserClaimsListCommand(@NotNull HuskClaims plugin) {
        super(
                List.of("claimslist", "claims"),
                "[player] [sort_by] [ascending|descending] [page]",
                plugin
        );
    }

    @Override
    public void execute(@NotNull CommandUser executor, @NotNull String[] args) {
        final Optional<User> optionalUser = resolveUser(executor, args);
        final SortOption sort = parseSortArg(args, 1).or(() -> parseSortArg(args, 0)).orElse(SortOption.SIZE);
        final boolean ascend = parseOrderArg(args, 2).or(() -> parseOrderArg(args, 1)).orElse(false);
        final int page = parseIntArg(args, 3).or(() -> parseIntArg(args, 2)).orElse(1);
        if (optionalUser.isEmpty()) {
            plugin.getLocales().getLocale("error_invalid_syntax", getUsage())
                    .ifPresent(executor::sendMessage);
            return;
        }

        final User user = optionalUser.get();
        if (executor instanceof OnlineUser other && !other.equals(user) && !hasPermission(executor, "other")) {
            plugin.getLocales().getLocale("error_no_permission")
                    .ifPresent(executor::sendMessage);
            return;
        }

        showUserClaimList(executor, user, page, sort, ascend);
    }

    protected void showUserClaimList(@NotNull CommandUser executor, @NotNull User user,
                                     int page, @NotNull SortOption sort, boolean ascend) {
        if (claimLists.containsKey(user.getUuid())) {
            showClaimList(executor, user, claimLists.get(user.getUuid()), page, sort, ascend);
            return;
        }

        final List<ServerWorldClaim> claims = Lists.newArrayList(getUserClaims(user));
        if (claims.isEmpty()) {
            plugin.getLocales().getLocale("error_no_claims_made", user.getName())
                    .ifPresent(executor::sendMessage);
            return;
        }
        claimLists.put(user.getUuid(), claims);

        showClaimList(executor, user, claims, page, sort, ascend);
    }

    @NotNull
    private List<ServerWorldClaim> getUserClaims(@NotNull User user) {
        return plugin.getDatabase().getAllClaimWorlds().entrySet().stream()
                .flatMap(e -> e.getValue().getClaims().stream()
                        .filter(c -> user.getUuid().equals(c.getOwner().orElse(null)))
                        .map(c -> new ServerWorldClaim(e.getKey(), c)))
                .toList();
    }

    @Override
    @NotNull
    protected String getListTitle(@NotNull Locales locales, @Nullable User user, int claimCount, @NotNull SortOption sort, boolean ascend) {
        return locales.getRawLocale(
                "user_claim_list_title",
                locales.getRawLocale(
                        String.format("claim_list_sort_%s", ascend ? "ascending" : "descending"),
                        sort.getId(),
                        "%current_page%"
                ).orElse(""),
                Locales.escapeText(user != null ? user.getName() : locales.getNotApplicable()),
                Integer.toString(claimCount),
                locales.getRawLocale(
                        "claim_list_sort_options",
                        getSortButtons(locales, sort, ascend)
                ).orElse("")
        ).orElse("");
    }
}
