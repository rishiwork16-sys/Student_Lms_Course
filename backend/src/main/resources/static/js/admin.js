// Dashboard Logic
let currentCourseData = null;

async function loadStats() {
    try {
        const res = await fetchWithAuth('/admin/reports');
        const data = await res.json();
        const grid = document.getElementById('statsGrid');
        if (grid) {
            grid.innerHTML = `
                <div class="stat-card">
                    <p>Total Users</p>
                    <h3>${data.totalUsers}</h3>
                </div>
                <div class="stat-card">
                    <p>Total Courses</p>
                    <h3>${data.totalCourses}</h3>
                </div>
                <div class="stat-card">
                    <p>Enrollments</p>
                    <h3>${data.totalEnrollments}</h3>
                </div>
                 <div class="stat-card">
                    <p>Revenue</p>
                    <h3>₹${data.revenue}</h3>
                </div>
            `;
        }
    } catch (e) {
        console.error(e);
    }
}

async function loadCourses() {
    try {
        const res = await fetchWithAuth('/admin/courses');
        const courses = await res.json();
        const grid = document.getElementById('coursesGrid');
        if (grid) {
            grid.innerHTML = courses.map(c => `
                <div class="course-card" onclick="window.location.href='course-manage.html?id=${c.id}'">
                    <img src="${c.thumbnail ? c.thumbnail : 'https://images.unsplash.com/photo-1516321318423-f06f85e504b3?w=800&q=80'}" alt="Thumb" onerror="this.src='https://images.unsplash.com/photo-1516321318423-f06f85e504b3?w=800&q=80'">
                    <div class="info">
                        <h4>${c.title}</h4>
                        <p>₹${c.price}</p>
                        <small>${c.active ? 'Active' : 'Inactive'}</small>
                    </div>
                </div>
            `).join('');
        }
    } catch (e) {
        console.error(e);
    }
}

// =============================================
// STUDENT MANAGEMENT
// =============================================
let allStudents = [];
let currentStudentId = null;
let allCourses = [];

async function openStudentManageModal() {
    document.getElementById('studentManageModal').style.display = 'flex';
    switchTab('tabStudentList', document.querySelector('#studentManageModal .tab-btn'));
    await loadStudentList();
}

async function loadStudentList() {
    const container = document.getElementById('studentListContainer');
    if (!container) return;
    container.innerHTML = '<div class="empty-state"><i class="fas fa-spinner fa-spin"></i> Loading students...</div>';
    try {
        const res = await fetchWithAuth('/admin/students');
        allStudents = await res.json();
        renderStudentList(allStudents);
    } catch (e) {
        container.innerHTML = '<div class="empty-state">Failed to load students.</div>';
    }
}

function renderStudentList(students) {
    const container = document.getElementById('studentListContainer');
    if (!container) return;
    if (!students.length) {
        container.innerHTML = '<div class="empty-state"><i class="fas fa-user-slash"></i><br>No students found.</div>';
        return;
    }
    container.innerHTML = students.map(s => `
        <div class="student-card">
            <div class="student-avatar">${(s.name || 'S').charAt(0).toUpperCase()}</div>
            <div class="student-info">
                <h4>${s.name || 'Unnamed'}</h4>
                <p>${s.phone || ''} ${s.email ? '• ' + s.email : ''}</p>
            </div>
            <div class="student-actions">
                <button class="btn-sm primary" onclick="openStudentDetail(${s.id}, '${(s.name || '').replace(/'/g, "\\'")}'  , '${s.phone || ''}', '${s.email || ''}')">
                    <i class="fas fa-eye"></i> Manage
                </button>
            </div>
        </div>
    `).join('');
}

function filterStudentList(query) {
    const q = query.toLowerCase();
    const filtered = allStudents.filter(s =>
        (s.name || '').toLowerCase().includes(q) ||
        (s.phone || '').includes(q) ||
        (s.email || '').toLowerCase().includes(q)
    );
    renderStudentList(filtered);
}

async function openStudentDetail(userId, name, phone, email) {
    currentStudentId = userId;
    document.getElementById('studentDetailAvatar').textContent = (name || 'S').charAt(0).toUpperCase();
    document.getElementById('studentDetailName').textContent = name || 'Unknown';
    document.getElementById('studentDetailMeta').textContent = `📞 ${phone || 'N/A'}  ${email ? '• ✉️ ' + email : ''}`;

    switchTab('tabEnrollments', document.querySelector('#studentDetailsModal .tab-btn'));
    document.getElementById('studentDetailsModal').style.display = 'flex';
    await loadStudentEnrollments(userId);
    await loadCoursesForAssign();
}

async function loadStudentEnrollments(userId) {
    const container = document.getElementById('enrollmentListContainer');
    container.innerHTML = '<div class="empty-state"><i class="fas fa-spinner fa-spin"></i> Loading...</div>';
    try {
        const res = await fetchWithAuth(`/admin/students/${userId}/enrollments`);
        const enrollments = await res.json();
        if (!enrollments.length) {
            container.innerHTML = '<div class="empty-state"><i class="fas fa-book-open"></i><br>No courses enrolled yet.</div>';
            return;
        }
        container.innerHTML = enrollments.map(e => `
            <div class="enrollment-item">
                <span><i class="fas fa-check-circle" style="color:#38a169;margin-right:8px;"></i>${e.courseTitle}</span>
                <button class="btn-sm danger" onclick="removeEnrollment(${e.enrollmentId}, this)">
                    <i class="fas fa-times"></i> Remove
                </button>
            </div>
        `).join('');
    } catch (e) {
        container.innerHTML = '<div class="empty-state">Failed to load enrollments.</div>';
    }
}

async function loadCoursesForAssign() {
    try {
        const res = await fetchWithAuth('/admin/courses');
        allCourses = await res.json();
        const select = document.getElementById('assignCourseSelect');
        select.innerHTML = '<option value="">-- Select Course --</option>' +
            allCourses.map(c => `<option value="${c.id}">${c.title}</option>`).join('');
    } catch (e) { console.error(e); }
}

async function submitAssignCourse() {
    const courseId = document.getElementById('assignCourseSelect').value;
    if (!courseId) { alert('Please select a course'); return; }
    try {
        const res = await fetchWithAuth(`/admin/students/${currentStudentId}/courses/${courseId}`, { method: 'POST' });
        const text = await res.text();
        if (res.ok) {
            alert('✅ Course assigned successfully!');
            await loadStudentEnrollments(currentStudentId);
            switchTab('tabEnrollments', document.querySelector('#studentDetailsModal .tab-btn'));
        } else {
            alert('❌ ' + text);
        }
    } catch (e) { alert('Error assigning course'); }
}

async function removeEnrollment(enrollmentId, btn) {
    if (!confirm('Remove this course from the student?')) return;
    btn.disabled = true; btn.textContent = '...';
    try {
        const res = await fetchWithAuth(`/admin/enrollments/${enrollmentId}`, { method: 'DELETE' });
        if (res.ok) {
            btn.closest('.enrollment-item').remove();
            const container = document.getElementById('enrollmentListContainer');
            if (!container.querySelector('.enrollment-item')) {
                container.innerHTML = '<div class="empty-state"><i class="fas fa-book-open"></i><br>No courses enrolled yet.</div>';
            }
        } else {
            alert('Failed to remove enrollment');
            btn.disabled = false; btn.innerHTML = '<i class="fas fa-times"></i> Remove';
        }
    } catch (e) { alert('Error'); btn.disabled = false; }
}

async function confirmDeleteStudent() {
    const name = document.getElementById('studentDetailName').textContent;
    if (!confirm(`⚠️ Delete student "${name}" and all their enrollments permanently?`)) return;
    try {
        const res = await fetchWithAuth(`/admin/students/${currentStudentId}`, { method: 'DELETE' });
        if (res.ok) {
            alert('Student deleted.');
            closeModal('studentDetailsModal');
            await loadStudentList();
        } else {
            alert('Failed to delete student.');
        }
    } catch (e) { alert('Error deleting student'); }
}

async function submitAddStudent(e) {
    e.preventDefault();
    const name = document.getElementById('newStudentName').value;
    const phone = document.getElementById('newStudentPhone').value;
    const email = document.getElementById('newStudentEmail').value;
    const password = document.getElementById('newStudentPassword').value;
    try {
        const res = await fetchWithAuth('/admin/students', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, phone, email, password })
        });
        const text = await res.text();
        if (res.ok) {
            alert('✅ Student added successfully!');
            document.getElementById('addStudentForm').reset();
            switchTab('tabStudentList', document.querySelector('#studentManageModal .tab-btn'));
            await loadStudentList();
        } else {
            alert('❌ ' + text);
        }
    } catch (e) { alert('Error adding student'); }
}

function switchTab(tabId, clickedBtn) {
    // Find the parent modal
    const modal = clickedBtn ? clickedBtn.closest('.modal-content') : null;
    const panes = modal ? modal.querySelectorAll('.tab-pane') : document.querySelectorAll('.tab-pane');
    const btns = modal ? modal.querySelectorAll('.tab-btn') : document.querySelectorAll('.tab-btn');
    panes.forEach(p => p.classList.remove('active'));
    btns.forEach(b => b.classList.remove('active'));
    const pane = document.getElementById(tabId);
    if (pane) pane.classList.add('active');
    if (clickedBtn) clickedBtn.classList.add('active');
}

// Legacy - kept for backward compatibility
async function loadStudents() { /* handled by student manage modal now */ }
async function viewStudentDetails(userId) { /* handled by openStudentDetail */ }



// Create Course
function openCreateModal() {
    document.getElementById('createModal').style.display = 'flex';
    // Reset form
    document.getElementById('createCourseForm').reset();
    document.getElementById('courseTypeFree').checked = true;
    togglePriceFields();
}
function closeCreateModal() { document.getElementById('createModal').style.display = 'none'; }

// Toggle price fields based on course type
function togglePriceFields() {
    const isPaid = document.getElementById('courseTypePaid').checked;
    const priceFields = document.getElementById('priceFields');

    if (isPaid) {
        priceFields.style.display = 'block';
        document.getElementById('courseMrp').required = true;
        document.getElementById('coursePrice').required = true;
    } else {
        priceFields.style.display = 'none';
        document.getElementById('courseMrp').required = false;
        document.getElementById('coursePrice').required = false;
    }
}

// Calculate and display discount percentage
function calculateDiscount() {
    const mrp = parseFloat(document.getElementById('courseMrp').value) || 0;
    const price = parseFloat(document.getElementById('coursePrice').value) || 0;
    const discountDisplay = document.getElementById('discountDisplay');

    if (mrp > 0 && price > 0 && price < mrp) {
        const discount = ((mrp - price) / mrp * 100).toFixed(0);
        discountDisplay.textContent = `🎉 ${discount}% OFF (Save ₹${(mrp - price).toFixed(2)})`;
    } else if (price > mrp && mrp > 0) {
        discountDisplay.textContent = '⚠️ Selling price is higher than MRP';
        discountDisplay.style.color = '#ef4444';
    } else {
        discountDisplay.textContent = '';
    }
}

document.getElementById('createCourseForm')?.addEventListener('submit', async (e) => {
    e.preventDefault();

    const title = document.getElementById('courseTitle').value;
    const desc = document.getElementById('courseDesc').value;
    const duration = parseInt(document.getElementById('courseDuration').value);
    const isPaid = document.getElementById('courseTypePaid').checked;
    const active = document.getElementById('courseActive').checked;
    const file = document.getElementById('courseThumbnail').files[0];
    const category = document.getElementById('courseCategory').value;
    const language = document.getElementById('courseLanguage').value;
    const instructor = document.getElementById('courseInstructor').value;

    let price = 0;
    let mrp = 0;

    if (isPaid) {
        price = parseFloat(document.getElementById('coursePrice').value);
        mrp = parseFloat(document.getElementById('courseMrp').value);

        if (!price || price <= 0) {
            alert('Please enter a valid selling price for paid course');
            return;
        }
        if (!mrp || mrp <= 0) {
            alert('Please enter a valid MRP for paid course');
            return;
        }
    }

    const courseData = {
        title,
        description: desc,
        price,
        active,
        duration,
        category: category || null,
        language: language || null,
        mrp: mrp || null,
        instructor: instructor || null
    };

    const formData = new FormData();
    formData.append('course', JSON.stringify(courseData));
    if (file) formData.append('thumbnail', file);

    try {
        const res = await fetchWithAuth('/admin/courses', {
            method: 'POST',
            body: formData
        });

        if (res.ok) {
            closeCreateModal();
            loadCourses();
            loadStats(); // Update course count
            alert('Course created successfully!');
        } else {
            const error = await res.text();
            alert('Failed to create course: ' + error);
        }
    } catch (error) {
        console.error('Error creating course:', error);
        alert('Error creating course. Please try again.');
    }
});

// Course Management Logic
let currentCourseId = null;

async function loadCourseDetails(id) {
    currentCourseId = id;
    const res = await fetchWithAuth(`/admin/courses/${id}`);
    const course = await res.json();
    currentCourseData = course; // Store course data
    document.getElementById('courseTitleHeader').innerText = course.title;

    renderModules(course.modules);
}

function renderModules(modules) {
    const container = document.getElementById('modulesContainer');
    container.innerHTML = modules.map(m => `
        <div class="module-item">
            <div class="module-header">
                <h3><i class="fas fa-folder-open"></i> ${m.title}</h3>
                <div class="module-actions">
                    <button class="btn-icon" onclick="openVideoModal(${m.id})" title="Add Video">
                        <i class="fas fa-video"></i> Add Video
                    </button>
                    <button class="btn-icon" onclick="openAssignModal(${m.id})" title="Add Assignment">
                        <i class="fas fa-file-alt"></i> Add Assignment
                    </button>
                    <button class="btn-icon" onclick="openExamModal(${m.id})" title="Add Exam">
                        <i class="fas fa-plus-circle"></i> Add Exam
                    </button>
                    <button class="btn-icon" onclick="window.location.href='exam-builder.html?courseId=${currentCourseId}&moduleId=${m.id}'" title="Exam Builder">
                        <i class="fas fa-edit"></i> Exam Builder
                    </button>
                    <button class="btn-icon" onclick="editModule(${m.id}, '${m.title}')" title="Rename Module">
                        <i class="fas fa-edit"></i>
                    </button>
                    <button class="btn-icon btn-delete" onclick="deleteModuleConfirm(${m.id}, '${m.title}')" title="Delete Module">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>
            </div>
            <div class="videos-list">
                <h4><i class="fas fa-play-circle"></i> Videos</h4>
                ${m.videos.length === 0 ? '<p class="empty-msg">No videos yet</p>' :
            m.videos.map(v => `
                    <div class="video-item">
                        <span><i class="fas fa-play-circle"></i> ${v.title}</span>
                        <div class="item-actions">
                            <button class="btn-icon btn-play" onclick="playVideo(${v.id}, '${v.title}')" title="Play Video">
                                <i class="fas fa-play"></i> Play
                            </button>
                            <button class="btn-icon" onclick="editVideo(${v.id}, '${v.title}')" title="Edit Video">
                                <i class="fas fa-edit"></i> Edit
                            </button>
                            <button class="btn-icon btn-delete" onclick="deleteVideoConfirm(${v.id}, '${v.title}')" title="Delete Video">
                                <i class="fas fa-trash"></i>
                            </button>
                        </div>
                    </div>
                `).join('')}
            </div>
            <div class="assign-list">
                <h4><i class="fas fa-file-alt"></i> Assignments</h4>
                ${m.assignments.length === 0 ? '<p class="empty-msg">No assignments yet</p>' :
            m.assignments.map(a => `
                    <div class="assign-item">
                        <span>
                            <i class="${a.type === 'FILE' ? 'fas fa-file-download' : 'fas fa-file-alt'}"></i> 
                            ${a.title}
                            ${a.type === 'FILE' ? `<span class="file-tag">File</span>` : ''}
                        </span>
                        <div class="item-actions">
                            <button class="btn-icon" onclick="viewAssignment(${a.id})" title="View Assignment">
                                <i class="fas fa-eye"></i>
                            </button>
                            <button class="btn-icon" onclick="openEditAssignment(${a.id})" title="Edit Assignment">
                                <i class="fas fa-edit"></i>
                            </button>
                            <button class="btn-icon btn-delete" onclick="deleteAssignmentConfirm(${a.id}, '${a.title}')" title="Delete Assignment">
                                <i class="fas fa-trash"></i>
                            </button>
                        </div>
                    </div>
                `).join('')}
            </div>
            <div class="exams-list">
                <h4><i class="fas fa-edit"></i> Exams</h4>
                ${(m.exams || []).length === 0 ? '<p class="empty-msg">No exams yet</p>' :
            m.exams.map(e => `
                    <div class="assign-item">
                        <span>
                            <i class="fas fa-clock"></i> 
                            ${e.title} (${e.durationMinutes}m)
                        </span>
                        <div class="item-actions">
                            <button class="btn-icon" onclick="viewExam(${e.id})" title="View Exam">
                                <i class="fas fa-eye"></i>
                            </button>
                            <button class="btn-icon" onclick="editExam(${m.id}, ${e.id})" title="Edit Exam">
                                <i class="fas fa-edit"></i>
                            </button>
                            <button class="btn-icon btn-delete" onclick="deleteExamConfirm(${e.id}, '${e.title}')" title="Delete Exam">
                                <i class="fas fa-trash"></i>
                            </button>
                        </div>
                    </div>
                `).join('')}
            </div>
        </div>
    `).join('');
}

// Modals
function closeModal(id) { document.getElementById(id).style.display = 'none'; }
function openAddModuleModal() { document.getElementById('moduleModal').style.display = 'flex'; }
async function submitModule() {
    const title = document.getElementById('moduleTitle').value;
    await fetchWithAuth('/admin/modules', {
        method: 'POST',
        body: JSON.stringify({ title, courseId: currentCourseId })
    });
    closeModal('moduleModal');
    loadCourseDetails(currentCourseId);
}

function openVideoModal(moduleId) {
    document.getElementById('currentModuleId').value = moduleId;
    document.getElementById('videoModal').style.display = 'flex';
}
async function submitVideo() {
    const moduleId = document.getElementById('currentModuleId').value;
    const title = document.getElementById('videoTitle').value;
    const file = document.getElementById('videoFile').files[0];

    if (!file) return alert("Select a file");

    const formData = new FormData();
    formData.append('moduleId', moduleId);
    formData.append('title', title);
    formData.append('file', file);

    document.getElementById('uploadStatus').innerText = "Uploading...";
    await fetchWithAuth('/admin/videos', { method: 'POST', body: formData });
    document.getElementById('uploadStatus').innerText = "";
    closeModal('videoModal');
    loadCourseDetails(currentCourseId);
}

function toggleAssignmentFields() {
    const type = document.getElementById('assignType').value;
    const textField = document.getElementById('textAssignmentFields');
    const fileField = document.getElementById('fileAssignmentFields');

    if (type === 'TEXT') {
        textField.style.display = 'block';
        fileField.style.display = 'none';
    } else {
        textField.style.display = 'none';
        fileField.style.display = 'block';
    }
}

function openAssignModal(moduleId) {
    document.getElementById('assignModuleId').value = moduleId;
    // Reset form
    document.getElementById('assignTitle').value = '';
    document.getElementById('assignTitle').value = '';
    document.getElementById('assignDesc').value = '';
    document.getElementById('assignTextContent').value = '';
    document.getElementById('assignFile').value = '';
    document.getElementById('assignType').value = 'TEXT';
    toggleAssignmentFields(); // Show text fields by default
    document.getElementById('assignmentModal').style.display = 'flex';
}
async function submitAssignment() {
    const moduleId = document.getElementById('assignModuleId').value;
    const title = document.getElementById('assignTitle').value;
    const type = document.getElementById('assignType').value;

    const formData = new FormData();
    formData.append('moduleId', moduleId);
    formData.append('title', title);
    formData.append('type', type);

    const desc = document.getElementById('assignDesc').value;
    if (desc) formData.append('description', desc);

    if (type === 'TEXT') {
        const content = document.getElementById('assignTextContent').value;
        if (!content || content.trim().length === 0) {
            alert('Please enter assignment content');
            return;
        }
        formData.append('textContent', content);
    } else {
        const file = document.getElementById('assignFile').files[0];
        if (!file) {
            alert('Please select a file');
            return;
        }
        formData.append('file', file);
    }

    await fetchWithAuth('/admin/assignments', {
        method: 'POST',
        body: formData
    });
    closeModal('assignmentModal');
    loadCourseDetails(currentCourseId);
}

// EXAM BUILDER FUNCTIONS
function openExamModal(moduleId) {
    document.getElementById('examModuleId').value = moduleId;
    document.getElementById('examTitle').value = '';
    document.getElementById('examDuration').value = '60';
    document.getElementById('examPassingMarks').value = '40';
    document.getElementById('questionsContainer').innerHTML = '';
    document.getElementById('examModal').style.display = 'flex';
    // Add one MCQ by default
    addQuestionRow('MCQ');
}

function initExamBuilderPage() {
    const params = new URLSearchParams(window.location.search);
    const moduleId = parseInt(params.get('moduleId'));
    const examId = params.get('examId') ? parseInt(params.get('examId')) : null;
    const courseIdParam = params.get('courseId');
    if (courseIdParam) {
        currentCourseId = parseInt(courseIdParam);
    }
    const questionList = document.getElementById('questionList');
    const addMcqBtn = document.getElementById('addMcqBtn');
    const addFileBtn = document.getElementById('addFileBtn');
    const addQuestionBottom = document.getElementById('addQuestionBottom');
    const publishBtn = document.getElementById('publishBtn');
    const editorTitle = document.getElementById('editorTitle');
    const removeQuestionBtn = document.getElementById('removeQuestionBtn');
    const qTextInput = document.getElementById('qTextInput');
    const qMarksInput = document.getElementById('qMarksInput');
    const mcqFields = document.getElementById('mcqFields');
    const optA = document.getElementById('optA');
    const optB = document.getElementById('optB');
    const optC = document.getElementById('optC');
    const optD = document.getElementById('optD');
    const correctSelect = document.getElementById('correctSelect');
    const discardBtn = document.getElementById('discardBtn');
    const saveBtn = document.getElementById('saveBtn');
    const examTitleInput = document.getElementById('examTitleInput');
    const durationMinutesInput = document.getElementById('durationMinutesInput');
    const passingMarksInput = document.getElementById('passingMarksInput');
    const unsavedBanner = document.getElementById('unsavedBanner');
    const totalsInfo = document.getElementById('totalsInfo');

    if (!moduleId) {
        alert('moduleId missing. Open this page via Course Manage → Exam Builder');
        return;
    }

    const state = { moduleId, examId, title: '', passingMarks: 40, durationMinutes: 60, questions: [], selectedIndex: -1, unsaved: false };

    function renderList() {
        questionList.innerHTML = state.questions.map((q, i) => `
            <div class="question-list-item ${state.selectedIndex === i ? 'active' : ''}">
                <div style="display:flex; gap:10px; align-items:center; flex:1;" onclick="selectExamQuestion(${i})">
                    <div class="question-list-index">${i + 1}</div>
                    <div>
                        <div style="font-weight:600;">${q.type}</div>
                        <div style="font-size:12px; color:#64748b;">${q.marks} marks</div>
                    </div>
                </div>
                <div style="display:flex; gap:6px;">
                    <button class="btn-icon" title="Move Up" onclick="moveExamQuestionUp(${i})"><i class="fas fa-arrow-up"></i></button>
                    <button class="btn-icon" title="Move Down" onclick="moveExamQuestionDown(${i})"><i class="fas fa-arrow-down"></i></button>
                    <button class="btn-icon btn-delete" title="Delete" onclick="removeExamQuestionAt(${i})"><i class="fas fa-trash"></i></button>
                </div>
            </div>
        `).join('');
        renderTotals();
    }

    function select(index) {
        state.selectedIndex = index;
        const q = state.questions[index];
        editorTitle.textContent = `Question ${index + 1} (${q.type})`;
        removeQuestionBtn.style.display = 'inline-block';
        qTextInput.value = q.questionText || '';
        qMarksInput.value = q.marks || 10;
        if (q.type === 'MCQ') {
            mcqFields.style.display = 'block';
            optA.value = q.options?.[0] || '';
            optB.value = q.options?.[1] || '';
            optC.value = q.options?.[2] || '';
            optD.value = q.options?.[3] || '';
            const idx = ['A', 'B', 'C', 'D'].indexOf(q.correctKey || 'A');
            correctSelect.value = ['A', 'B', 'C', 'D'][idx >= 0 ? idx : 0];
        } else {
            mcqFields.style.display = 'none';
            optA.value = ''; optB.value = ''; optC.value = ''; optD.value = '';
            correctSelect.value = 'A';
        }
        state.unsaved = false;
        updateUnsavedBanner();
    }

    function add(type) {
        state.questions.push({ type, questionText: '', marks: 10, options: type === 'MCQ' ? ['', '', '', ''] : null, correctKey: type === 'MCQ' ? 'A' : null });
        state.selectedIndex = state.questions.length - 1;
        renderList();
        select(state.selectedIndex);
    }

    function discard() {
        if (state.selectedIndex < 0) return;
        select(state.selectedIndex);
    }

    function save() {
        if (state.selectedIndex < 0) { alert('Select a question'); return; }
        const q = state.questions[state.selectedIndex];
        q.questionText = qTextInput.value.trim();
        q.marks = parseInt(qMarksInput.value) || 0;
        if (q.type === 'MCQ') {
            q.options = [optA.value.trim(), optB.value.trim(), optC.value.trim(), optD.value.trim()];
            q.correctKey = correctSelect.value;
        }
        renderList();
        state.unsaved = false;
        updateUnsavedBanner();
    }

    function removeSelected() {
        if (state.selectedIndex < 0) return;
        state.questions.splice(state.selectedIndex, 1);
        state.selectedIndex = -1;
        editorTitle.textContent = 'Select a question to edit';
        removeQuestionBtn.style.display = 'none';
        qTextInput.value = '';
        qMarksInput.value = 10;
        mcqFields.style.display = 'none';
        renderList();
    }

    function removeAt(index) {
        if (index < 0 || index >= state.questions.length) return;
        state.questions.splice(index, 1);
        if (state.selectedIndex === index) {
            state.selectedIndex = -1;
            editorTitle.textContent = 'Select a question to edit';
            removeQuestionBtn.style.display = 'none';
            qTextInput.value = '';
            qMarksInput.value = 10;
            mcqFields.style.display = 'none';
        } else if (state.selectedIndex > index) {
            state.selectedIndex -= 1;
        }
        renderList();
    }

    function moveUp(index) {
        if (index <= 0 || index >= state.questions.length) return;
        const tmp = state.questions[index];
        state.questions.splice(index, 1);
        state.questions.splice(index - 1, 0, tmp);
        if (state.selectedIndex === index) state.selectedIndex = index - 1;
        renderList();
    }

    function moveDown(index) {
        if (index < 0 || index >= state.questions.length - 1) return;
        const tmp = state.questions[index];
        state.questions.splice(index, 1);
        state.questions.splice(index + 1, 0, tmp);
        if (state.selectedIndex === index) state.selectedIndex = index + 1;
        renderList();
    }

    function markUnsaved() {
        state.unsaved = true;
        updateUnsavedBanner();
    }

    function updateUnsavedBanner() {
        if (!unsavedBanner) return;
        unsavedBanner.style.display = state.unsaved ? 'block' : 'none';
    }

    function renderTotals() {
        if (!totalsInfo) return;
        const count = state.questions.length;
        const totalMarks = state.questions.reduce((sum, qq) => sum + (parseInt(qq.marks) || 0), 0);
        totalsInfo.textContent = `${count} questions • ${totalMarks} total marks`;
    }

    async function publish() {
        state.title = examTitleInput.value.trim();
        state.passingMarks = parseInt(passingMarksInput.value) || 0;
        state.durationMinutes = parseInt(durationMinutesInput.value) || 60;
        if (state.unsaved) { alert('Please save or discard changes before publishing'); return; }
        if (!state.title) { alert('Enter exam title'); return; }
        if (state.questions.length === 0) { alert('Add at least one question'); return; }
        const questionsPayload = state.questions.map(q => {
            const payload = { questionText: q.questionText, type: q.type, marks: q.marks };
            if (q.type === 'MCQ') {
                payload.options = q.options;
                const idx = ['A', 'B', 'C', 'D'].indexOf(q.correctKey || 'A');
                payload.correctAnswer = q.options[idx >= 0 ? idx : 0] || '';
            } else {
                payload.correctAnswer = '';
            }
            return payload;
        });
        const payload = {
            moduleId: state.moduleId,
            title: state.title,
            description: '',
            durationMinutes: state.durationMinutes,
            passingMarks: state.passingMarks,
            questions: questionsPayload
        };
        try {
            const url = state.examId ? `/admin/exams/${state.examId}` : '/admin/exams';
            const method = state.examId ? 'PUT' : 'POST';
            const res = await fetchWithAuth(url, { method, body: JSON.stringify(payload) });
            if (res.ok) {
                alert('Exam published');
                window.location.href = `course-manage.html?id=${currentCourseId || ''}`;
            } else {
                alert(await res.text());
            }
        } catch (e) {
            alert('Failed to publish');
        }
    }

    window.selectExamQuestion = select;
    addMcqBtn.onclick = () => add('MCQ');
    addFileBtn.onclick = () => add('FILE_UPLOAD');
    addQuestionBottom.onclick = () => add('MCQ');
    discardBtn.onclick = discard;
    saveBtn.onclick = save;
    removeQuestionBtn.onclick = removeSelected;
    publishBtn.onclick = publish;
    window.removeExamQuestionAt = removeAt;
    window.moveExamQuestionUp = moveUp;
    window.moveExamQuestionDown = moveDown;

    (async () => {
        if (examId) {
            try {
                const res = await fetchWithAuth(`/admin/exams/${examId}`);
                if (res.ok) {
                    const exam = await res.json();
                    state.title = exam.title || '';
                    state.passingMarks = exam.passingMarks || 40;
                    state.durationMinutes = exam.durationMinutes || 60;
                    examTitleInput.value = state.title;
                    passingMarksInput.value = state.passingMarks;
                    if (durationMinutesInput) durationMinutesInput.value = state.durationMinutes;
                    state.questions = (exam.questions || []).map(q => ({
                        type: q.type,
                        questionText: q.questionText,
                        marks: q.marks,
                        options: q.options || ['', '', '', ''],
                        correctKey: 'A'
                    }));
                    renderList();
                    if (state.questions.length > 0) select(0);
                }
            } catch { }
        }
    })();

    // Mark unsaved when editing fields
    [qTextInput, qMarksInput, optA, optB, optC, optD, correctSelect].forEach(el => {
        if (!el) return;
        const evt = el.tagName === 'SELECT' ? 'change' : 'input';
        el.addEventListener(evt, markUnsaved);
    });
    // Keyboard shortcut for save
    document.addEventListener('keydown', (e) => {
        if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
            e.preventDefault();
            save();
        }
    });
}

function addQuestionRow(type) {
    const container = document.getElementById('questionsContainer');
    const index = container.children.length;
    const row = document.createElement('div');
    row.className = 'question-row-card';
    row.style = 'border: 1px solid #e2e8f0; padding: 15px; margin-bottom: 15px; border-radius: 8px; position: relative;';

    let html = `
        <button type="button" class="btn-icon btn-delete" style="position: absolute; right: 10px; top: 10px;" onclick="this.parentElement.remove()">
            <i class="fas fa-times"></i>
        </button>
        <div style="margin-bottom: 10px;">
            <label>Question ${index + 1} (${type})</label>
            <input type="text" class="q-text" placeholder="Enter question text" style="width: 100%;" required>
            <input type="hidden" class="q-type" value="${type}">
        </div>
        <div style="display: flex; gap: 15px; margin-bottom: 10px;">
            <div style="flex: 1;">
                <label>Marks</label>
                <input type="number" class="q-marks" value="10" style="width: 100%;">
            </div>
        </div>
    `;

    if (type === 'MCQ') {
        html += `
            <div class="mcq-options" style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px;">
                <div><label>Option A</label><input type="text" class="opt-a" placeholder="Option A" required></div>
                <div><label>Option B</label><input type="text" class="opt-b" placeholder="Option B" required></div>
                <div><label>Option C</label><input type="text" class="opt-c" placeholder="Option C" required></div>
                <div><label>Option D</label><input type="text" class="opt-d" placeholder="Option D" required></div>
            </div>
            <div style="margin-top: 10px;">
                <label>Correct Option</label>
                <select class="q-correct">
                    <option value="A">Option A</option>
                    <option value="B">Option B</option>
                    <option value="C">Option C</option>
                    <option value="D">Option D</option>
                </select>
            </div>
        `;
    }

    row.innerHTML = html;
    container.appendChild(row);
}

async function submitExam() {
    const moduleId = document.getElementById('examModuleId').value;
    const title = document.getElementById('examTitle').value;
    const durationMinutes = document.getElementById('examDuration').value;
    const passingMarks = document.getElementById('examPassingMarks').value;

    const questionRows = document.querySelectorAll('.question-row-card');
    const questions = [];

    questionRows.forEach(row => {
        const type = row.querySelector('.q-type').value;
        const qData = {
            questionText: row.querySelector('.q-text').value,
            type: type,
            marks: parseInt(row.querySelector('.q-marks').value)
        };

        if (type === 'MCQ') {
            qData.options = [
                row.querySelector('.opt-a').value,
                row.querySelector('.opt-b').value,
                row.querySelector('.opt-c').value,
                row.querySelector('.opt-d').value
            ];
            const correctOpt = row.querySelector('.q-correct').value;
            const correctIdx = correctOpt === 'A' ? 0 : correctOpt === 'B' ? 1 : correctOpt === 'C' ? 2 : 3;
            qData.correctAnswer = qData.options[correctIdx];
        } else {
            qData.correctAnswer = ""; // For File Upload, grading is manual
        }

        questions.push(qData);
    });

    if (questions.length === 0) {
        alert("Please add at least one question");
        return;
    }

    const payload = {
        moduleId: parseInt(moduleId),
        title,
        description: "",
        durationMinutes: parseInt(durationMinutes),
        passingMarks: parseInt(passingMarks),
        questions: questions
    };

    try {
        const res = await fetchWithAuth('/admin/exams', {
            method: 'POST',
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            closeModal('examModal');
            loadCourseDetails(currentCourseId);
            alert("Exam created successfully!");
        } else {
            const err = await res.text();
            alert("Failed to create exam: " + err);
        }
    } catch (e) {
        console.error(e);
        alert("Error creating exam");
    }
}

function logout() {
    localStorage.removeItem('token');
    window.location.href = 'login.html';
}

// Scroll to courses section
function scrollToCourses() {
    const coursesSection = document.getElementById('coursesGrid');
    if (coursesSection) {
        coursesSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
}

function scrollToStudents() {
    const studentsSection = document.getElementById('studentsGrid');
    if (studentsSection) {
        studentsSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
}

// ============== VIDEO PLAY & DELETE FUNCTIONS ==============

// Play Video
async function playVideo(videoId, title) {
    try {
        const res = await fetchWithAuth(`/admin/videos/${videoId}/preview`);
        if (res.ok) {
            const data = await res.json();
            document.getElementById('playerVideoTitle').innerText = title;
            const player = document.getElementById('adminVideoPlayer');
            player.src = data.signedUrl;
            document.getElementById('videoPlayerModal').style.display = 'flex';
            player.play();
            initAdminPlayerControls();
        } else {
            alert('Failed to load video');
        }
    } catch (e) {
        console.error(e);
        alert('Error loading video');
    }
}

function closeVideoPlayer() {
    const player = document.getElementById('adminVideoPlayer');
    player.pause();
    player.src = '';
    document.getElementById('videoPlayerModal').style.display = 'none';
}

function initAdminPlayerControls() {
    const player = document.getElementById('adminVideoPlayer');
    const playBtn = document.getElementById('adminPlayBtn');
    const muteBtn = document.getElementById('adminMuteBtn');
    const vol = document.getElementById('adminVolumeRange');
    const timeLabel = document.getElementById('adminTimeLabel');
    const speed = document.getElementById('adminSpeedSelect');
    const pip = document.getElementById('adminPipBtn');
    const fs = document.getElementById('adminFsBtn');
    const theater = document.getElementById('adminTheaterBtn');
    const modal = document.querySelector('.video-player-modal');

    playBtn.onclick = () => {
        if (player.paused) { player.play(); playBtn.innerHTML = '<i class="fas fa-pause"></i>'; }
        else { player.pause(); playBtn.innerHTML = '<i class="fas fa-play"></i>'; }
    };
    player.onplay = () => { playBtn.innerHTML = '<i class="fas fa-pause"></i>'; };
    player.onpause = () => { playBtn.innerHTML = '<i class="fas fa-play"></i>'; };

    muteBtn.onclick = () => {
        player.muted = !player.muted;
        muteBtn.innerHTML = player.muted ? '<i class="fas fa-volume-mute"></i>' : '<i class="fas fa-volume-up"></i>';
    };
    vol.oninput = () => { player.volume = parseFloat(vol.value); if (player.volume === 0) { player.muted = true; muteBtn.innerHTML = '<i class="fas fa-volume-mute"></i>'; } else { player.muted = false; muteBtn.innerHTML = '<i class="fas fa-volume-up"></i>'; } };

    const fmt = s => {
        const m = Math.floor(s / 60); const sec = Math.floor(s % 60);
        return String(m).padStart(2, '0') + ':' + String(sec).padStart(2, '0');
    };
    const updateTime = () => {
        const cur = isFinite(player.currentTime) ? player.currentTime : 0;
        const dur = isFinite(player.duration) ? player.duration : 0;
        timeLabel.textContent = `${fmt(cur)} / ${fmt(dur)}`;
    };
    player.ontimeupdate = updateTime;
    player.onloadedmetadata = updateTime;

    speed.onchange = () => { player.playbackRate = parseFloat(speed.value); };
    pip.onclick = async () => { if ('requestPictureInPicture' in player) { try { await player.requestPictureInPicture(); } catch { } } };
    fs.onclick = () => { if (player.requestFullscreen) player.requestFullscreen(); };
    theater.onclick = () => { modal.classList.toggle('theater'); };
}
// ============== DELETE FUNCTIONS ==============

let deleteType = null;
let deleteId = null;

function deleteVideoConfirm(id, title) {
    deleteType = 'video';
    deleteId = id;
    document.getElementById('deleteMessage').innerText = `Are you sure you want to delete video "${title}"? This will also remove it from S3.`;
    document.getElementById('deleteModal').style.display = 'flex';
}

function deleteModuleConfirm(id, title) {
    deleteType = 'module';
    deleteId = id;
    document.getElementById('deleteMessage').innerText = `Are you sure you want to delete module "${title}"? This will also delete all videos and assignments inside it.`;
    document.getElementById('deleteModal').style.display = 'flex';
}

function deleteAssignmentConfirm(id, title) {
    deleteType = 'assignment';
    deleteId = id;
    document.getElementById('deleteMessage').innerText = `Are you sure you want to delete assignment "${title}"?`;
    document.getElementById('deleteModal').style.display = 'flex';
}

function deleteExamConfirm(id, title) {
    deleteType = 'exam';
    deleteId = id;
    document.getElementById('deleteMessage').innerText = `Are you sure you want to delete exam "${title}"? This will also delete all student submissions for this exam.`;
    document.getElementById('deleteModal').style.display = 'flex';
}

async function confirmDelete() {
    if (!deleteType || !deleteId) return;

    let endpoint = '';
    if (deleteType === 'video') endpoint = `/admin/videos/${deleteId}`;
    else if (deleteType === 'module') endpoint = `/admin/modules/${deleteId}`;
    else if (deleteType === 'assignment') endpoint = `/admin/assignments/${deleteId}`;
    else if (deleteType === 'exam') endpoint = `/admin/exams/${deleteId}`;
    else if (deleteType === 'course') endpoint = `/admin/courses/${deleteId}`;

    try {
        const res = await fetchWithAuth(endpoint, { method: 'DELETE' });
        if (res.ok) {
            closeModal('deleteModal');
            if (deleteType === 'course') {
                alert('Course deleted successfully!');
                window.location.href = 'dashboard.html';
            } else {
                loadCourseDetails(currentCourseId);
                alert(`${deleteType.charAt(0).toUpperCase() + deleteType.slice(1)} deleted successfully!`);
            }
        } else {
            alert('Failed to delete: ' + await res.text());
        }
    } catch (e) {
        console.error(e);
        alert('Error deleting item');
    }

    deleteType = null;
    deleteId = null;
}

// ============== COURSE EDIT & DELETE ==============

function editCourse() {
    if (!currentCourseData) return;
    document.getElementById('editCourseTitle').value = currentCourseData.title;
    document.getElementById('editCourseDesc').value = currentCourseData.description || '';
    document.getElementById('editCoursePrice').value = currentCourseData.price;
    document.getElementById('editCourseActive').value = currentCourseData.active ? 'true' : 'false';
    document.getElementById('editCourseModal').style.display = 'flex';
}

async function submitCourseEdit() {
    const title = document.getElementById('editCourseTitle').value;
    const description = document.getElementById('editCourseDesc').value;
    const price = document.getElementById('editCoursePrice').value;
    const active = document.getElementById('editCourseActive').value === 'true';

    const formData = new FormData();
    const priceVal = parseFloat(price);
    formData.append('course', JSON.stringify({
        title,
        description,
        price: isNaN(priceVal) ? null : priceVal,
        active
    }));

    try {
        const res = await fetchWithAuth(`/admin/courses/${currentCourseId}`, {
            method: 'PUT',
            body: formData
        });

        if (res.ok) {
            closeModal('editCourseModal');
            loadCourseDetails(currentCourseId);
            alert('Course updated successfully!');
        } else {
            alert('Failed to update course');
        }
    } catch (e) {
        console.error(e);
        alert('Error updating course');
    }
}

async function toggleCourseStatus() {
    if (!currentCourseData) return;
    const newActive = !currentCourseData.active;
    const formData = new FormData();
    formData.append('course', JSON.stringify({ active: newActive }));
    try {
        const res = await fetchWithAuth(`/admin/courses/${currentCourseId}`, {
            method: 'PUT',
            body: formData
        });
        if (res.ok) {
            await loadCourseDetails(currentCourseId);
            alert(`Course ${newActive ? 'activated' : 'deactivated'} successfully!`);
        } else {
            alert('Failed to toggle status');
        }
    } catch (e) {
        console.error(e);
        alert('Error toggling status');
    }
}

function deleteCourseConfirm() {
    if (!currentCourseData) return;
    deleteType = 'course';
    deleteId = currentCourseId;
    document.getElementById('deleteMessage').innerText = `Are you sure you want to delete course "${currentCourseData.title}"? This will delete all modules, videos (from S3), and assignments.`;
    document.getElementById('deleteModal').style.display = 'flex';
}

// ============== MODULE EDIT ==============

function editModule(id, currentTitle) {
    document.getElementById('editModuleId').value = id;
    document.getElementById('editModuleTitle').value = currentTitle;
    document.getElementById('editModuleModal').style.display = 'flex';
}

async function submitModuleEdit() {
    const id = document.getElementById('editModuleId').value;
    const title = document.getElementById('editModuleTitle').value;

    try {
        const res = await fetchWithAuth(`/admin/modules/${id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ title })
        });

        if (res.ok) {
            closeModal('editModuleModal');
            loadCourseDetails(currentCourseId);
            alert('Module updated successfully!');
        } else {
            alert('Failed to update module');
        }
    } catch (e) {
        console.error(e);
        alert('Error updating module');
    }
}

// ============== VIDEO EDIT (Rename & Replace) ==============

function editVideo(id, currentTitle) {
    document.getElementById('editVideoId').value = id;
    document.getElementById('editVideoTitle').value = currentTitle;
    document.getElementById('editVideoFile').value = '';
    document.getElementById('editVideoModal').style.display = 'flex';
}

async function submitVideoEdit() {
    const id = document.getElementById('editVideoId').value;
    const title = document.getElementById('editVideoTitle').value;
    const file = document.getElementById('editVideoFile').files[0];

    const formData = new FormData();
    if (title) formData.append('title', title);
    if (file) formData.append('file', file);

    try {
        const res = await fetchWithAuth(`/admin/videos/${id}`, {
            method: 'PUT',
            body: formData
        });

        if (res.ok) {
            closeModal('editVideoModal');
            loadCourseDetails(currentCourseId);
            alert('Video updated successfully!');
        } else {
            alert('Failed to update video');
        }
    } catch (e) {
        console.error(e);
        alert('Error updating video');
    }
}

// Auto load if on dashboard
if (window.location.pathname.endsWith('dashboard.html')) {
    loadStats();
    loadCourses();
    loadStudents();
}

async function loadPayments() {
    try {
        const res = await fetchWithAuth('/admin/payments');
        const payments = await res.json();
        const grid = document.getElementById('paymentsGrid');
        if (grid) {
            grid.innerHTML = `
                <div class="stat-card" style="grid-column: 1 / -1;">
                    <div style="overflow-x:auto;">
                        <table style="width:100%; border-collapse: collapse;">
                            <thead>
                                <tr>
                                    <th style="text-align:left; padding:8px; border-bottom:1px solid #e2e8f0;">Order ID</th>
                                    <th style="text-align:left; padding:8px; border-bottom:1px solid #e2e8f0;">Payment ID</th>
                                    <th style="text-align:left; padding:8px; border-bottom:1px solid #e2e8f0;">User</th>
                                    <th style="text-align:left; padding:8px; border-bottom:1px solid #e2e8f0;">Course</th>
                                    <th style="text-align:left; padding:8px; border-bottom:1px solid #e2e8f0;">Amount</th>
                                    <th style="text-align:left; padding:8px; border-bottom:1px solid #e2e8f0;">Status</th>
                                    <th style="text-align:left; padding:8px; border-bottom:1px solid #e2e8f0;">Created</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${payments.map(p => {
                const statusClass = p.status ? p.status.toLowerCase() : 'pending';
                return `
                                    <tr class="payment-row">
                                        <td style="padding:12px 8px;">${p.orderId || '-'}</td>
                                        <td style="padding:12px 8px;">${p.paymentId || '-'}</td>
                                        <td style="padding:12px 8px;">${p.userName || '-'}</td>
                                        <td style="padding:12px 8px;">${p.courseId || '-'}</td>
                                        <td style="padding:12px 8px; font-weight:600;">₹${p.amount != null ? p.amount : '-'}</td>
                                        <td style="padding:12px 8px;">
                                            <span class="status-badge status-${statusClass}">${p.status || 'PENDING'}</span>
                                        </td>
                                        <td style="padding:12px 8px; color:#64748b;">${p.createdAt ? new Date(p.createdAt).toLocaleDateString() : '-'}</td>
                                    </tr>
                                    `;
            }).join('')}
                            </tbody>
                        </table>
                    </div>
                </div>
            `;
        }
    } catch (e) {
        console.error(e);
    }
}

function viewAssignment(id) {
    if (!currentCourseData) return;
    let target = null;
    currentCourseData.modules.forEach(m => {
        (m.assignments || []).forEach(a => { if (a.id === id) target = a; });
    });
    if (!target) { alert('Assignment not found'); return; }
    document.getElementById('viewAssignTitle').innerText = target.title || 'Assignment';
    document.getElementById('viewAssignType').innerText = `Type: ${target.type}`;
    document.getElementById('viewAssignDesc').innerText = target.description || '';
    const textDiv = document.getElementById('viewAssignText');
    const fileDiv = document.getElementById('viewAssignFile');
    if (target.type === 'TEXT') {
        textDiv.style.display = 'block';
        fileDiv.style.display = 'none';
        textDiv.innerText = target.textContent || '';
    } else {
        textDiv.style.display = 'none';
        fileDiv.style.display = 'block';
        fileDiv.innerHTML = '<span>Loading file link...</span>';
        fetchWithAuth(`/admin/assignments/${id}/file`).then(async res => {
            if (res.ok) {
                const d = await res.json();
                fileDiv.innerHTML = `<a href="${d.fileUrl}" target="_blank" class="btn-secondary">Open File</a>`;
            } else {
                fileDiv.innerHTML = 'Unable to load file';
            }
        }).catch(() => { fileDiv.innerHTML = 'Unable to load file'; });
    }
    document.getElementById('viewAssignmentModal').style.display = 'flex';
}

function openEditAssignment(id) {
    if (!currentCourseData) return;
    let target = null, type = 'TEXT';
    currentCourseData.modules.forEach(m => {
        (m.assignments || []).forEach(a => { if (a.id === id) { target = a; type = a.type; } });
    });
    if (!target) { alert('Assignment not found'); return; }
    document.getElementById('editAssignId').value = id;
    document.getElementById('editAssignTitle').value = target.title || '';
    document.getElementById('editAssignDesc').value = target.description || '';
    const textFields = document.getElementById('editAssignTextFields');
    const fileField = document.getElementById('editAssignFileField');
    if (type === 'TEXT') {
        textFields.style.display = 'block';
        fileField.style.display = 'none';
        document.getElementById('editAssignTextContent').value = target.textContent || '';
    } else {
        textFields.style.display = 'none';
        fileField.style.display = 'block';
        document.getElementById('editAssignFile').value = '';
    }
    document.getElementById('editAssignmentModal').style.display = 'flex';
}

async function submitAssignmentEdit() {
    const id = document.getElementById('editAssignId').value;
    const title = document.getElementById('editAssignTitle').value;
    const description = document.getElementById('editAssignDesc').value;
    let type = 'TEXT';
    currentCourseData.modules.forEach(m => {
        (m.assignments || []).forEach(a => { if (String(a.id) === String(id)) { type = a.type; } });
    });
    const formData = new FormData();
    if (title) formData.append('title', title);
    if (description) formData.append('description', description);
    if (type === 'TEXT') {
        const content = document.getElementById('editAssignTextContent').value;
        formData.append('textContent', content || '');
    } else {
        const file = document.getElementById('editAssignFile').files[0];
        if (file) formData.append('file', file);
    }
    const res = await fetchWithAuth(`/admin/assignments/${id}`, { method: 'PUT', body: formData });
    if (res.ok) {
        closeModal('editAssignmentModal');
        await loadCourseDetails(currentCourseId);
        alert('Assignment updated successfully!');
    } else {
        alert('Failed to update assignment');
    }
}

async function viewExam(id) {
    const res = await fetchWithAuth(`/admin/exams/${id}`);
    if (!res.ok) { alert('Failed to load exam'); return; }
    const exam = await res.json();
    document.getElementById('viewExamTitle').innerText = exam.title || 'Exam';
    document.getElementById('viewExamMeta').innerText = `Duration: ${exam.durationMinutes}m • Passing: ${exam.passingMarks}`;
    const list = (exam.questions || []).map((q, i) => `
        <div style="border:1px solid #e2e8f0; padding:12px; border-radius:8px; margin-bottom:10px;">
            <div style="font-weight:600;">${i + 1}. ${q.questionText}</div>
            <div style="color:#64748b;">${q.type} • ${q.marks} marks</div>
            ${q.options ? `<div style="margin-top:8px; display:grid; grid-template-columns:1fr 1fr; gap:8px;">
                ${q.options.map((o, idx) => `<div style="padding:8px; background:#f8fafc; border-radius:6px;">${String.fromCharCode(65 + idx)}. ${o}</div>`).join('')}
            </div>` : ''}
        </div>
    `).join('');
    document.getElementById('viewExamQuestions').innerHTML = list || '<p>No questions</p>';
    document.getElementById('viewExamModal').style.display = 'flex';
}

function editExam(moduleId, examId) {
    window.location.href = `exam-builder.html?courseId=${currentCourseId}&moduleId=${moduleId}&examId=${examId}`;
}

// Global Mobile Navigation Toggle
document.addEventListener('DOMContentLoaded', () => {
    const toggle = document.getElementById('adminNavToggle');
    const menu = document.querySelector('.sidebar-menu');

    if (toggle && menu) {
        toggle.addEventListener('click', (e) => {
            e.stopPropagation(); // Prevent immediate closing
            menu.classList.toggle('open');

            // Update icon
            const icon = toggle.querySelector('i');
            if (menu.classList.contains('open')) {
                icon.classList.remove('fa-bars');
                icon.classList.add('fa-times');
            } else {
                icon.classList.remove('fa-times');
                icon.classList.add('fa-bars');
            }
        });

        // Close menu when clicking outside
        document.addEventListener('click', (e) => {
            if (menu.classList.contains('open') && !menu.contains(e.target) && !toggle.contains(e.target)) {
                menu.classList.remove('open');
                const icon = toggle.querySelector('i');
                icon.classList.remove('fa-times');
                icon.classList.add('fa-bars');
            }
        });
    }
});
