package lk.ac.sliit.tgms.search;

import java.util.List;

public record SharedSearchPage(
        String search,
        int page,
        int size,
        int totalResults,
        int totalPages,
        List<SharedSearchResult> results) {

    public SharedSearchPage {
        results = List.copyOf(results);
    }

    public static SharedSearchPage from(
            String search, int page, int size, List<SharedSearchResult> allResults) {
        int totalResults = allResults.size();
        int totalPages = totalResults == 0 ? 0 : (totalResults + size - 1) / size;
        int fromIndex = Math.min(page * size, totalResults);
        int toIndex = Math.min(fromIndex + size, totalResults);
        return new SharedSearchPage(
                search,
                page,
                size,
                totalResults,
                totalPages,
                allResults.subList(fromIndex, toIndex));
    }
}
