package lk.ac.sliit.tgms.search;

import java.util.List;

/** One server-paginated module section returned by TGMS-74 shared search. */
public record SharedSearchSection(
        String module,
        String label,
        int page,
        int size,
        boolean hasNext,
        List<SharedSearchResult> results) {

    public SharedSearchSection {
        results = List.copyOf(results);
    }

    public static SharedSearchSection from(
            String module,
            String label,
            int page,
            int size,
            List<SharedSearchResult> fetched) {
        boolean hasNext = fetched.size() > size;
        List<SharedSearchResult> visible = hasNext
                ? List.copyOf(fetched.subList(0, size))
                : List.copyOf(fetched);
        return new SharedSearchSection(module, label, page, size, hasNext, visible);
    }
}
