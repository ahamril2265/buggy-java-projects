package com.library.repository;

import com.library.model.Member;

import java.util.LinkedHashMap;
import java.util.Map;

public class MemberRepository {
    private final Map<String, Member> membersById = new LinkedHashMap<>();

    public void save(Member member) {
        membersById.put(member.getMemberId(), member);
    }

    public Member findById(String memberId) {
        if (memberId == null) {
            return null;
        }
        return membersById.get(memberId);
    }
}
