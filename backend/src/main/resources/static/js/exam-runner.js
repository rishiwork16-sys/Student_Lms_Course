let currentExam = null;
let currentQuestionIndex = 0;
let answers = {}; // questionId -> selectedOptionText or fileKey
let timeLeft = 0;
let timerInterval = null;
let submissionId = null;

const urlParams = new URLSearchParams(window.location.search);
const examId = urlParams.get('examId');
const courseId = urlParams.get('courseId');

document.addEventListener('DOMContentLoaded', async () => {
    if (!examId) {
        alert("Invalid Exam ID");
        window.location.href = 'dashboard.html';
        return;
    }
    await startExamFlow();
});

async function startExamFlow() {
    try {
        const res = await fetchWithAuthNoRedirect(`/student/exams/${examId}/start`, {
            method: 'POST'
        });

        if (res.ok) {
            currentExam = await res.json();
            submissionId = currentExam.submissionId;
            timeLeft = currentExam.durationMinutes * 60;
            renderExamInfo();
            renderQuestion();
            startTimer();
            return;
        }

        const status = res.status;
        const errText = (await res.text()) || '';

        if (status === 401 || status === 403) {
            alert("Session expired. Please login again to start the exam.");
            return;
        }

        const friendly = errText && errText.toLowerCase().includes('already')
            ? 'You have already attempted this exam.'
            : (errText || 'Unable to start exam at the moment.');
        alert(friendly);
        window.location.href = `player.html?id=${courseId}`;
    } catch (e) {
        console.error(e);
        alert("Failed to start exam");
    }
}

function renderExamInfo() {
    document.getElementById('examTitle').innerText = currentExam.title;
    const qGrid = document.getElementById('qGrid');
    qGrid.innerHTML = currentExam.questions.map((q, i) => `
        <div class="q-num-btn" id="qBtn-${i}" onclick="goToQuestion(${i})">${i + 1}</div>
    `).join('');
}

function renderQuestion() {
    const q = currentExam.questions[currentQuestionIndex];
    const container = document.getElementById('questionContent');

    // Update active button
    document.querySelectorAll('.q-num-btn').forEach(btn => btn.classList.remove('active'));
    document.getElementById(`qBtn-${currentQuestionIndex}`).classList.add('active');

    let html = `
        <div style="font-size:20px; font-weight:600; margin-bottom: 20px;">
            Q${currentQuestionIndex + 1}. ${q.questionText}
            <span style="font-size:14px; color:#64748b; margin-left:10px;">(${q.marks} Marks)</span>
        </div>
    `;

    if (q.type === 'MCQ') {
        html += `<div class="options-container">
            ${q.options.map((opt, i) => `
                <div class="option-item ${answers[q.id] === i ? 'selected' : ''}" onclick="selectOption('${q.id}', ${i})">
                    <span class="opt-index">${String.fromCharCode(65 + i)}.</span>
                    <span class="opt-text">${opt}</span>
                    <span class="opt-dot" aria-hidden="true"></span>
                </div>
            `).join('')}
        </div>`;
    } else if (q.type === 'FILE_UPLOAD') {
        html += `
            <div style="padding: 20px; border: 2px dashed #e2e8f0; border-radius: 12px; text-align: center;">
                <input type="file" id="qFile-${q.id}" onchange="uploadFile('${q.id}')" style="display:none;">
                <label for="qFile-${q.id}" class="btn-exam btn-next" style="display:inline-block; cursor:pointer;">
                    <i class="fas fa-cloud-upload-alt"></i> Choose File
                </label>
                <div id="fileName-${q.id}" style="margin-top:10px; color:#10b981; font-weight:600;">
                    ${answers[q.id] ? 'File Uploaded ✓' : 'No file chosen'}
                </div>
            </div>
        `;
    }

    container.innerHTML = html;

    // Update buttons
    document.getElementById('prevBtn').disabled = currentQuestionIndex === 0;

    const nextBtn = document.getElementById('nextBtn');
    if (currentQuestionIndex === currentExam.questions.length - 1) {
        nextBtn.innerText = 'Finish review';
        nextBtn.onclick = confirmSubmit;
    } else {
        nextBtn.innerText = 'Next';
        nextBtn.onclick = nextQuestion;
    }
}

function selectOption(qId, optionIndex) {
    answers[qId] = optionIndex;
    document.getElementById(`qBtn-${currentQuestionIndex}`).classList.add('answered');
    renderQuestion();
}

async function uploadFile(qId) {
    const fileInput = document.getElementById(`qFile-${qId}`);
    if (!fileInput.files[0]) return;

    const formData = new FormData();
    formData.append('file', fileInput.files[0]);

    try {
        const res = await fetchWithAuthNoRedirect('/student/exams/upload', {
            method: 'POST',
            body: formData
        });

        if (res.ok) {
            const data = await res.json();
            answers[qId] = data.fileKey;
            document.getElementById(`qBtn-${currentQuestionIndex}`).classList.add('answered');
            renderQuestion();
            alert("File uploaded successfully!");
        } else {
            alert("Upload failed: " + await res.text());
        }
    } catch (e) {
        console.error(e);
        alert("Error uploading file");
    }
}

function goToQuestion(index) {
    currentQuestionIndex = index;
    renderQuestion();
}

function nextQuestion() {
    if (currentQuestionIndex < currentExam.questions.length - 1) {
        currentQuestionIndex++;
        renderQuestion();
    }
}

function prevQuestion() {
    if (currentQuestionIndex > 0) {
        currentQuestionIndex--;
        renderQuestion();
    }
}

function startTimer() {
    timerInterval = setInterval(() => {
        timeLeft--;
        if (timeLeft <= 0) {
            clearInterval(timerInterval);
            alert("Time's up! Submitting your exam.");
            submitExam();
        } else {
            const m = Math.floor(timeLeft / 60);
            const s = timeLeft % 60;
            document.getElementById('timer').innerText = `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
        }
    }, 1000);
}

function confirmSubmit() {
    const unanswered = currentExam.questions.length - Object.keys(answers).length;
    let msg = "Are you sure you want to submit the exam?";
    if (unanswered > 0) msg += ` You have ${unanswered} unanswered questions.`;

    if (confirm(msg)) {
        submitExam();
    }
}

async function submitExam() {
    clearInterval(timerInterval);
    const payload = {
        submissionId: submissionId,
        answers: Object.keys(answers).map(qId => {
            const q = currentExam.questions.find(x => String(x.id) === String(qId));
            let ansVal = answers[qId];
            if (typeof ansVal === 'number' && q && Array.isArray(q.options)) {
                ansVal = q.options[ansVal] ?? '';
            }
            return {
                questionId: parseInt(qId),
                answer: ansVal
            };
        })
    };

    try {
        const res = await fetchWithAuthNoRedirect('/student/exams/submit', {
            method: 'POST',
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            const result = await res.json();
            alert(`Exam submitted! You scored ${result.obtainedMarks} marks. ${result.passed ? 'PASSED' : 'FAILED'}`);
            window.location.href = `player.html?id=${courseId}`;
        } else {
            const status = res.status;
            const errText = await res.text();
            if (status === 401 || status === 403) {
                alert("Session issue while submitting. Please login again.");
            } else {
                alert("Failed to submit exam: " + (errText || 'Unknown error'));
            }
        }
    } catch (e) {
        console.error(e);
        alert("Error submitting exam");
    }
}
