package com.aiscientist.ai.wangwanying.evidence;

import java.util.List;

public interface EvidenceRepository {
    Evidence save(Evidence evidence);
    List<Evidence> findAll();
    List<Evidence> search(String query, int limit);
}
