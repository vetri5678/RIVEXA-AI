package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.entity.ProjectEntity;
import ai.riskvision.graveyard.entity.UserEntity;
import ai.riskvision.graveyard.repository.ProjectRepository;
import ai.riskvision.graveyard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<ProjectEntity> getMyProjects(String email, String search, String status, int page, int size, String sortBy, boolean sortDesc) {
        UserEntity owner = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));

        Sort sort;
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            sort = Sort.by(sortDesc ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        } else {
            sort = Sort.by(Sort.Direction.DESC, "createdAt");
        }

        Pageable pageable = PageRequest.of(page, size, sort);
        return projectRepository.findByOwnerAndFilters(owner, search, status, pageable);
    }
}
