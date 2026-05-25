package com.eventsystem.application.company;

import com.eventsystem.application.member.MemberRepository;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.Permission;
import com.eventsystem.domain.company.ProductionCompany;
import com.eventsystem.application.company.ProductionCompanyRepository;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionCompanyServiceConcurrencyTest {

    @Test
    void twoThreadsAppointSameManagerExactlyOneSucceeds() throws Exception {
        InMemoryMemberRepository memberRepository = new InMemoryMemberRepository();
        InMemoryProductionCompanyRepository companyRepository = new InMemoryProductionCompanyRepository();
        ProductionCompanyService service = new ProductionCompanyService(companyRepository, memberRepository);

        MemberId founder = MemberId.random();
        MemberId manager = MemberId.random();
        memberRepository.save(new Member(founder));
        memberRepository.save(new Member(manager));

        CompanyId companyId = service.createCompany(founder, "Concurrent", "desc", 4.0);

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger();

        Runnable task = () -> {
            ready.countDown();
            try {
                start.await(2, TimeUnit.SECONDS);
                service.appointManager(companyId, founder, manager, Set.of(Permission.MODIFY_POLICIES));
                successCount.incrementAndGet();
            } catch (RuntimeException ignored) {
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        };

        pool.submit(task);
        pool.submit(task);

        assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(3, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(successCount.get()).isEqualTo(1);
    }

    private static final class InMemoryMemberRepository implements MemberRepository {
        private final Map<MemberId, Member> members = new ConcurrentHashMap<>();

        @Override
        public Optional<Member> findById(MemberId memberId) {
            return Optional.ofNullable(members.get(memberId));
        }

        @Override
        public Optional<Member> findByUsername(String username) {
            return members.values().stream()
                    .filter(m -> m.getUsername().equals(username))
                    .findFirst();
        }

        @Override
        public void save(Member member) {
            members.put(member.memberId(), member);
        }
    }

    private static final class InMemoryProductionCompanyRepository implements ProductionCompanyRepository {
        private final Map<CompanyId, ProductionCompany> companiesById = new ConcurrentHashMap<>();
        private final Map<String, CompanyId> names = new ConcurrentHashMap<>();

        @Override
        public Optional<ProductionCompany> findById(CompanyId companyId) {
            return Optional.ofNullable(companiesById.get(companyId));
        }

        @Override
        public Optional<ProductionCompany> findByName(String companyName) {
            CompanyId id = names.get(companyName.toLowerCase());
            if (id == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(companiesById.get(id));
        }

        @Override
        public void save(ProductionCompany productionCompany) {
            names.put(productionCompany.companyDetails().name().toLowerCase(), productionCompany.companyId());
            companiesById.put(productionCompany.companyId(), productionCompany);
        }

        @Override
        public boolean hasPermission(MemberId memberId, CompanyId companyId, Permission eventInventoryManagement) {
            return findById(companyId)
                    .map(company -> company.hasPermission(memberId, eventInventoryManagement))
                    .orElse(false);
        }
    }
}
