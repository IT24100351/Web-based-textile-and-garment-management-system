package lk.ac.sliit.tgms.search;

public record SharedSearchResult(
        String module,
        long recordId,
        String title,
        String subtitle,
        String status,
        String path) {}
