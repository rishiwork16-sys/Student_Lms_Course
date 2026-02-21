let isNewUser = false;
let loginMode = 'phone';
let existingUserEmail = null;
let existingUserPhone = null;

async function sendOtp() {
    if (loginMode === 'phone') {
        const phone = document.getElementById('phone').value;
        const cc = document.getElementById('countryCode') ? document.getElementById('countryCode').value : '';
        const fullPhone = `${cc}${phone}`.replace(/[^0-9]/g, '');
        if (!phone) return showMessage("Please enter phone number", 'error');
        try {
            const status = await fetchUserStatusByPhone(fullPhone);
            if (status && status.exists) {
                if (status.email) {
                    existingUserEmail = status.email;
                    existingUserPhone = fullPhone;
                    showPasswordStep(fullPhone, status.email);
                    return;
                }
            } else {
                showMessage("New registrations are currently closed. Please contact support.", 'error');
                return;
            }
        } catch (e) {
            console.error(e);
        }
        try {
            const res = await fetchWithAuth('/auth/student/otp', {
                method: 'POST',
                body: JSON.stringify({ phone: fullPhone })
            });
            if (res.ok) {
                const data = await res.text();
                document.getElementById('step1').style.display = 'none';
                document.getElementById('step2').style.display = 'block';
                document.getElementById('phoneDisplay').innerText = fullPhone;
                checkUserExists(fullPhone);
                setupOtpInputs();
                startResendCountdown(30);
                const match = data.match(/(\d{6})/);
                if (match) {
                    const otpDigits = match[1].split('');
                    const inputs = document.querySelectorAll('.otp-input');
                    inputs.forEach((i, idx) => { i.value = otpDigits[idx] || ''; });
                    updateContinueButtonState();
                    showMessage("OTP sent successfully.", 'success');
                } else {
                    showMessage("OTP sent successfully! Check console for OTP.", 'success');
                }
                console.log("OTP Response:", data);
            } else {
                const err = await res.text().catch(() => '');
                showMessage(err || "Failed to send OTP", 'error');
            }
        } catch (e) {
            console.error(e);
            showMessage("Error sending OTP", 'error');
        }
    } else {
        const email = document.getElementById('loginEmail').value.trim().toLowerCase();
        if (!email) return showMessage("Please enter email", 'error');
        try {
            const status = await fetchUserStatusByEmail(email);
            if (!status || !status.exists) {
                showMessage("New registrations are currently closed. Please contact support.", 'error');
                return;
            }
            const res = await fetchWithAuth('/auth/student/email/otp', {
                method: 'POST',
                body: JSON.stringify({ email })
            });
            if (res.ok) {
                await res.text();
                document.getElementById('step1').style.display = 'none';
                document.getElementById('step2').style.display = 'block';
                document.getElementById('phoneDisplay').innerText = email;
                checkUserExistsByEmail(email);
                setupOtpInputs();
                startResendCountdown(30);
                showMessage("OTP sent successfully.", 'success');
            } else {
                const err = await res.text().catch(() => '');
                showMessage(err || "Failed to send OTP", 'error');
            }
        } catch (e) {
            console.error(e);
            showMessage("Error sending OTP", 'error');
        }
    }
}

async function fetchUserStatusByPhone(phone) {
    const res = await fetchWithAuth(`/auth/student/check?phone=${encodeURIComponent(phone)}`);
    if (!res.ok) {
        return { exists: false };
    }
    const data = await res.json();
    return data;
}

async function fetchUserStatusByEmail(email) {
    const res = await fetchWithAuth(`/auth/student/email/check?email=${encodeURIComponent(email)}`);
    if (!res.ok) {
        return { exists: false };
    }
    const data = await res.json();
    return data;
}

async function checkUserExists(phone) {
    try {
        const res = await fetchWithAuth(`/auth/student/check?phone=${encodeURIComponent(phone)}`);
        if (res.ok) {
            const data = await res.json();
            const showReg = !data.exists;
            const regFields = document.getElementById('registrationFields');
            const continueBtn = document.querySelector('#step2 .btn-continue');

            if (showReg) {
                regFields.style.display = 'block';
                continueBtn.style.background = '#3b82f6';
                continueBtn.style.color = 'white';
                isNewUser = true;
            } else {
                regFields.style.display = 'none';
                continueBtn.style.background = '#e2e8f0';
                continueBtn.style.color = '#94a3b8';
                isNewUser = false;
            }
        } else {
            document.getElementById('registrationFields').style.display = 'block';
            isNewUser = true;
        }
    } catch {
        document.getElementById('registrationFields').style.display = 'block';
        isNewUser = true;
    }
}

function showPasswordStep(phone, email) {
    document.getElementById('step1').style.display = 'none';
    const otpStep = document.getElementById('step2');
    if (otpStep) otpStep.style.display = 'none';
    const pwdStep = document.getElementById('stepPassword');
    if (pwdStep) pwdStep.style.display = 'block';
    const phoneLabel = document.getElementById('passwordPhoneDisplay');
    if (phoneLabel) phoneLabel.innerText = phone;
    const emailInput = document.getElementById('passwordLoginEmail');
    if (emailInput) emailInput.value = email || '';
}

async function loginWithPassword() {
    const emailInput = document.getElementById('passwordLoginEmail');
    const passInput = document.getElementById('passwordLoginPassword');
    const email = emailInput ? emailInput.value.trim().toLowerCase() : '';
    const password = passInput ? passInput.value : '';
    if (!email) {
        return showMessage("User ID (email) is required", 'error');
    }
    if (!password || password.length < 8) {
        return showMessage("Password must be at least 8 characters", 'error');
    }
    try {
        const res = await fetchWithAuth('/auth/student/login-password', {
            method: 'POST',
            body: JSON.stringify({ email, password })
        });
        if (res.ok) {
            const data = await res.json();
            localStorage.setItem('token', data.token);
            localStorage.setItem('userName', data.name || 'Student');
            showMessage("Login successful! Redirecting...", 'success');
            setTimeout(() => {
                window.location.href = 'dashboard.html';
            }, 1000);
        } else {
            // Parse JSON error response and show clean message
            try {
                const errData = await res.json();
                showMessage(errData.message || "Login failed. Please try again.", 'error');
            } catch {
                showMessage("Login failed. Please try again.", 'error');
            }
        }
    } catch (e) {
        console.error(e);
        showMessage("Network error. Please check your connection.", 'error');
    }
}

async function verifyOtp() {
    const otp = Array.from(document.querySelectorAll('.otp-input')).map(i => i.value).join('').replace(/[^0-9]/g, '');
    const name = document.getElementById('name').value;
    const email = document.getElementById('email').value;
    const address = document.getElementById('address').value;

    if (!otp || otp.length !== 6) return showMessage("Please enter complete OTP", 'error');

    const regVisible = document.getElementById('registrationFields').style.display !== 'none';
    if (regVisible && !name) {
        return showMessage("Please enter your name", 'error');
    }

    try {
        if (loginMode === 'phone') {
            const cc = document.getElementById('countryCode') ? document.getElementById('countryCode').value : '';
            const basePhone = document.getElementById('phone').value;
            const phone = `${cc}${basePhone}`.replace(/[^0-9]/g, '');
            const res = await fetchWithAuth('/auth/student/verify', {
                method: 'POST',
                body: JSON.stringify({ phone, otp, name, email, address })
            });
            if (res.ok) {
                const data = await res.json();
                localStorage.setItem('token', data.token);
                localStorage.setItem('userName', data.name || 'Student');
                showMessage("Login successful! Redirecting...", 'success');
                setTimeout(() => {
                    window.location.href = 'dashboard.html';
                }, 1000);
            } else {
                const error = await res.text();
                showMessage(error || "Invalid OTP", 'error');
            }
        } else {
            const loginEmail = document.getElementById('loginEmail').value;
            const res = await fetchWithAuth('/auth/student/email/verify', {
                method: 'POST',
                body: JSON.stringify({ email: loginEmail, otp, name, address })
            });
            if (res.ok) {
                const data = await res.json();
                localStorage.setItem('token', data.token);
                localStorage.setItem('userName', data.name || 'Student');
                showMessage("Login successful! Redirecting...", 'success');
                setTimeout(() => {
                    window.location.href = 'dashboard.html';
                }, 1000);
            } else {
                const error = await res.text();
                showMessage(error || "Invalid OTP", 'error');
            }
        }
    } catch (e) {
        console.error(e);
        showMessage("Error verifying OTP", 'error');
    }
}

function setupOtpInputs() {
    const inputs = document.querySelectorAll('.otp-input');
    inputs.forEach((input, idx) => {
        input.value = '';
        input.addEventListener('input', (e) => {
            // Only allow numbers
            e.target.value = e.target.value.replace(/[^0-9]/g, '');
            if (e.target.value && idx < inputs.length - 1) {
                inputs[idx + 1].focus();
            }
            // Auto-submit when all filled
            const allFilled = Array.from(inputs).every(i => i.value);
            if (allFilled) {
                updateContinueButtonState();
            }
        });
        input.addEventListener('keydown', (e) => {
            if (e.key === 'Backspace' && !input.value && idx > 0) {
                inputs[idx - 1].focus();
            }
        });
        input.addEventListener('paste', (e) => {
            e.preventDefault();
            const pasteData = e.clipboardData.getData('text').replace(/[^0-9]/g, '').slice(0, 6);
            pasteData.split('').forEach((char, i) => {
                if (inputs[i]) inputs[i].value = char;
            });
            if (inputs[pasteData.length - 1]) {
                inputs[pasteData.length - 1].focus();
            }
        });
    });
    if (inputs[0]) inputs[0].focus();
}

function updateContinueButtonState() {
    const continueBtn = document.querySelector('#step2 .btn-continue');
    const otp = Array.from(document.querySelectorAll('.otp-input')).map(i => i.value).join('');
    const regVisible = document.getElementById('registrationFields').style.display !== 'none';

    if (otp.length === 6) {
        continueBtn.style.background = '#3b82f6';
        continueBtn.style.color = 'white';
        continueBtn.disabled = false;
    } else {
        continueBtn.style.background = '#e2e8f0';
        continueBtn.style.color = '#94a3b8';
        continueBtn.disabled = true;
    }
}

let resendTimer = null;
function startResendCountdown(seconds) {
    const btn = document.getElementById('resendBtn');
    const cd = document.getElementById('resendCountdown');
    if (!btn || !cd) return;
    btn.disabled = true;
    let remaining = seconds;
    cd.innerText = remaining;
    if (resendTimer) clearInterval(resendTimer);
    resendTimer = setInterval(() => {
        remaining -= 1;
        cd.innerText = remaining;
        if (remaining <= 0) {
            clearInterval(resendTimer);
            btn.disabled = false;
            btn.innerText = 'Resend OTP';
        }
    }, 1000);
}

async function resendOtp() {
    if (loginMode === 'phone') {
        const cc = document.getElementById('countryCode') ? document.getElementById('countryCode').value : '';
        const basePhone = document.getElementById('phone').value;
        const fullPhone = `${cc}${basePhone}`.replace(/[^0-9]/g, '');
        try {
            const res = await fetchWithAuth('/auth/student/otp', {
                method: 'POST',
                body: JSON.stringify({ phone: fullPhone })
            });
            if (res.ok) {
                startResendCountdown(30);
                const data = await res.text();
                const match = data.match(/(\\d{6})/);
                if (match) {
                    const otpDigits = match[1].split('');
                    const inputs = document.querySelectorAll('.otp-input');
                    inputs.forEach((i, idx) => { i.value = otpDigits[idx] || ''; });
                    updateContinueButtonState();
                }
                showMessage("OTP resent successfully!", 'success');
            } else {
                const err = await res.text().catch(() => '');
                showMessage(err || "Failed to resend OTP", 'error');
            }
        } catch (e) {
            console.error(e);
            showMessage("Error resending OTP", 'error');
        }
    } else {
        const loginEmail = document.getElementById('loginEmail').value;
        try {
            const res = await fetchWithAuth('/auth/student/email/otp', {
                method: 'POST',
                body: JSON.stringify({ email: loginEmail })
            });
            if (res.ok) {
                startResendCountdown(30);
                showMessage("OTP resent successfully!", 'success');
            } else {
                const err = await res.text().catch(() => '');
                showMessage(err || "Failed to resend OTP", 'error');
            }
        } catch (e) {
            console.error(e);
            showMessage("Error resending OTP", 'error');
        }
    }
}

function backToStep1() {
    const step2 = document.getElementById('step2');
    if (step2) step2.style.display = 'none';
    const stepPwd = document.getElementById('stepPassword');
    if (stepPwd) stepPwd.style.display = 'none';
    document.getElementById('step1').style.display = 'block';
    document.querySelectorAll('.otp-input').forEach(input => input.value = '');
    document.getElementById('name').value = '';
    document.getElementById('email').value = '';
    document.getElementById('address').value = '';
    document.getElementById('msg').innerText = '';
    document.getElementById('registrationFields').style.display = 'none';
}

function showMessage(message, type) {
    const msgEl = document.getElementById('msg');
    msgEl.innerText = message;
    msgEl.className = 'msg ' + type;
    msgEl.style.display = 'block';
}

// Dashboard Logic
async function loadStudentDashboard() {
    const resEnrolled = await fetchWithAuth('/student/courses');
    if (resEnrolled.ok) {
        const enrolled = await resEnrolled.json();
        const grid = document.getElementById('enrolledGrid');
        if (enrolled.length === 0) {
            grid.innerHTML = "<p style='text-align:center; padding:40px; color:#64748b;'>No courses enrolled yet.</p>";
        } else {
            grid.innerHTML = enrolled.map(c => `
                <div class="course-card" style="cursor: pointer;" onclick="window.location.href='player.html?id=${c.id}'">
                    <img src="${c.thumbnail || 'https://via.placeholder.com/300x180/3b82f6/ffffff?text=' + encodeURIComponent(c.title)}" onerror="this.src='https://via.placeholder.com/300x180/3b82f6/ffffff?text=Course'">
                    <div class="info">
                        <h4>${c.title}</h4>
                        <p style="color:#94a3b8; font-size:13px; margin:8px 0;">${c.description || 'Start learning now'}</p>
                        <span class="course-badge" style="margin-bottom:8px;">${c.price == 0 ? 'Free' : 'Paid'}</span>
                        ${typeof c.progressPercent === 'number' ? `
                            <div class="progress"><div class="progress-bar" style="width:${Math.max(0, Math.min(100, c.progressPercent))}%"></div></div>
                            <div class="progress-label">${Math.round(c.progressPercent)}% complete</div>
                        ` : ''}
                        <button class="btn-primary" style="margin-top:10px;" onclick="event.stopPropagation(); window.location.href='player.html?id=${c.id}'">Continue Learning <i class="fas fa-arrow-right"></i></button>
                    </div>
                </div>
            `).join('');
        }
    }
}

async function checkUserExistsByEmail(email) {
    const regFields = document.getElementById('registrationFields');
    const continueBtn = document.querySelector('#step2 .btn-continue');
    if (regFields) regFields.style.display = 'none';
    if (continueBtn) {
        continueBtn.style.background = '#3b82f6';
        continueBtn.style.color = 'white';
    }
    isNewUser = false;
}

document.addEventListener('DOMContentLoaded', () => {
    const link = document.getElementById('toggleEmailLink');
    if (link) {
        link.addEventListener('click', (e) => {
            e.preventDefault();
            if (loginMode === 'phone') {
                loginMode = 'email';
                document.querySelector('.phone-input-wrapper').style.display = 'none';
                document.getElementById('emailLoginGroup').style.display = 'block';
                link.textContent = 'Continue with Phone';
            } else {
                loginMode = 'phone';
                document.querySelector('.phone-input-wrapper').style.display = 'flex';
                document.getElementById('emailLoginGroup').style.display = 'none';
                link.textContent = 'Continue with Email';
            }
        });
    }
});

async function enroll(courseId, price) {
    console.log('Enroll clicked for course:', courseId, 'price:', price);
    try {
        const resEnrolled = await fetchWithAuth('/student/courses');
        if (resEnrolled.ok) {
            const enrolled = await resEnrolled.json();
            if (enrolled.some(c => Number(c.id) === Number(courseId))) {
                alert('Already enrolled');
                return;
            }
        }
    } catch (e) { console.error(e); }
    if (!confirm(price > 0 ? `Enroll for Rs.${price}?` : 'Enroll for FREE?')) return;

    if (price == 0) {
        // Free Enrollment
        try {
            showLoading('Enrolling...');
            const res = await fetchWithAuth(`/student/enroll/${courseId}`, { method: 'POST' });
            if (res.ok) {
                hideLoading();
                showSuccessMessage('Enrolled successfully! Redirecting to My Courses...');
                setTimeout(() => {
                    loadStudentDashboard();
                    window.scrollTo({ top: 0, behavior: 'smooth' });
                }, 1500);
            } else {
                hideLoading();
                const error = await res.text();
                if (typeof error === 'string' && error.toLowerCase().includes('already enrolled')) {
                    alert('Already enrolled');
                } else {
                    alert('Failed: ' + error);
                }
            }
        } catch (e) {
            console.error(e);
            hideLoading();
            alert('Error enrolling in course');
        }
    } else {
        // Paid Enrollment (Razorpay)
        try {
            // 1. Create Order
            showLoading('Creating order...');
            const resOrder = await fetchWithAuth('/student/payment/create-order', {
                method: 'POST',
                body: JSON.stringify({ courseId })
            });

            if (!resOrder.ok) {
                hideLoading();
                return alert("Failed to create order: " + await resOrder.text());
            }

            const orderData = await resOrder.json();

            // 2. Open Razorpay
            const options = {
                "key": orderData.keyId,
                "amount": orderData.amount * 100,
                "currency": "INR",
                "name": "skilledUp",
                "description": "Course Enrollment",
                "order_id": orderData.orderId,
                "handler": async function (response) {
                    // 3. Verify Payment
                    try {
                        showLoading('Verifying payment...');
                        const verifyRes = await fetchWithAuth('/student/payment/verify', {
                            method: 'POST',
                            body: JSON.stringify({
                                razorpayOrderId: response.razorpay_order_id,
                                razorpayPaymentId: response.razorpay_payment_id,
                                razorpaySignature: response.razorpay_signature,
                                courseId: courseId
                            })
                        });

                        if (verifyRes.ok) {
                            hideLoading();
                            showSuccessMessage('Payment Successful! Course Enrolled. Redirecting...');
                            setTimeout(() => {
                                loadStudentDashboard();
                                window.scrollTo({ top: 0, behavior: 'smooth' });
                            }, 2000);
                        } else {
                            hideLoading();
                            alert("Payment Verification Failed: " + await verifyRes.text());
                        }
                    } catch (e) {
                        console.error(e);
                        hideLoading();
                        alert("Error verifying payment");
                    }
                },
                "prefill": {
                    "name": localStorage.getItem('userName') || "Student",
                    "contact": ""
                },
                "theme": {
                    "color": "#3b82f6"
                }
            };

            const rzp1 = new Razorpay(options);
            rzp1.on('payment.failed', function (response) {
                alert("Payment Failed: " + response.error.description);
            });
            hideLoading();
            rzp1.open();

        } catch (e) {
            console.error(e);
            hideLoading();
            alert("Error initiating payment");
        }
    }
}

function showSuccessMessage(message) {
    const msgDiv = document.createElement('div');
    msgDiv.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        background: #10b981;
        color: white;
        padding: 16px 24px;
        border-radius: 8px;
        box-shadow: 0 4px 12px rgba(0,0,0,0.2);
        z-index: 10000;
        font-weight: 600;
        animation: slideIn 0.3s ease;
    `;
    msgDiv.textContent = message;
    document.body.appendChild(msgDiv);
    setTimeout(() => {
        msgDiv.remove();
    }, 3000);
}

function showLoading(message) {
    let overlay = document.getElementById('globalLoading');
    if (!overlay) {
        overlay = document.createElement('div');
        overlay.id = 'globalLoading';
        overlay.style.cssText = 'position:fixed;inset:0;background:rgba(0,0,0,0.4);display:flex;align-items:center;justify-content:center;z-index:10000';
        const box = document.createElement('div');
        box.style.cssText = 'background:#111827;color:#fff;padding:16px 24px;border-radius:8px;font-weight:600;box-shadow:0 8px 24px rgba(0,0,0,0.3)';
        box.id = 'globalLoadingText';
        box.textContent = message || 'Loading...';
        overlay.appendChild(box);
        document.body.appendChild(overlay);
    } else {
        const box = document.getElementById('globalLoadingText');
        if (box) box.textContent = message || 'Loading...';
        overlay.style.display = 'flex';
    }
}

function hideLoading() {
    const overlay = document.getElementById('globalLoading');
    if (overlay) overlay.remove();
}

async function loadPlayer(courseId) {
    const res = await fetchWithAuth(`/student/courses/${courseId}`);
    if (!res.ok) return alert("Access Denied");

    const course = await res.json();
    window.playerCourseData = course;
    document.getElementById('courseTitle').innerText = course.title;

    // Calculate Progress
    let totalItems = 0;
    let completedItems = 0;
    course.modules.forEach(m => {
        totalItems += (m.videos?.length || 0) + (m.assignments?.length || 0) + (m.exams?.length || 0);
        completedItems += (m.videos?.filter(v => v.completed).length || 0);
        completedItems += (m.assignments?.filter(a => a.completed).length || 0);
        completedItems += (m.exams?.filter(e => e.passed).length || 0);
    });
    const progressPercent = totalItems > 0 ? Math.round((completedItems / totalItems) * 100) : 0;
    document.getElementById('courseProgressBar').style.width = progressPercent + '%';
    document.getElementById('courseProgressText').innerText = progressPercent + '% Complete';

    const moduleList = document.getElementById('moduleList');
    moduleList.innerHTML = course.modules.map((m, index) => {
        const videosHtml = m.videos.map(v => {
            const isLocked = false; // unlocked
            return `
                <div class="video-link ${isLocked ? 'locked' : ''}" 
                     id="vid-${v.id}" 
                     data-module-index="${index}" 
                     ${isLocked ? '' : `onclick="selectVideo(this, '${v.id}', '${v.title}', ${index})"`}>
                    <i class="fas ${isLocked ? 'fa-lock' : 'fa-play-circle'}"></i> ${v.title}
                    ${v.completed ? '<i class="fas fa-check-circle"></i>' : ''}
                    ${isLocked ? '<span class="lock-overlay">Locked</span>' : ''}
                </div>
            `;
        }).join('');

        const assignmentsHtml = m.assignments.map(a => `
            <div class="assignment-link" id="asn-${a.id}" onclick="showAssignment('${a.title}', '${a.textContent || a.description || ''}', '${a.type}', '${a.id}', this)">
                <i class="fas fa-file-alt"></i> ${a.title}
                ${a.completed ? '<i class="fas fa-check-circle"></i>' : ''}
            </div>
        `).join('');

        const examsHtml = (m.exams || []).map(e => {
            const isLocked = false; // Locked removed by user request
            return `
                <div class="exam-link ${isLocked ? 'locked' : ''}" 
                     onclick="startExam('${e.id}', '${e.title}')">
                    <i class="fas ${isLocked ? 'fa-lock' : 'fa-edit'}"></i> ${e.title}
                    ${e.passed ? '<i class="fas fa-check-circle" style="color:#10b981;"></i>' : (e.completed ? '<i class="fas fa-times-circle" style="color:#ef4444;"></i>' : '')}
                    ${isLocked ? '<span class="lock-overlay">Locked</span>' : ''}
                </div>
            `;
        }).join('');

        return `
            <div class="module-item ${index === 0 ? 'active' : ''}" id="module-${index}" data-module-index="${index}">
                <div class="module-header" onclick="toggleModule(${index})">
                    <h4>${m.title}</h4>
                    <i class="fas fa-chevron-down"></i>
                </div>
                <div class="module-content" id="module-content-${index}">
                    ${videosHtml}
                    ${assignmentsHtml}
                    ${examsHtml}
                </div>
            </div>
        `;
    }).join('');

    checkCertificateAvailability(course);
}

function checkCertificateAvailability(course) {
    const certBtn = document.getElementById('downloadCertBtn');
    if (!certBtn || !course || !course.modules) return;

    let totalItems = 0;
    let completedItems = 0;

    course.modules.forEach(m => {
        // Videos
        (m.videos || []).forEach(v => {
            totalItems++;
            if (v.completed) completedItems++;
        });
        // Assignments
        (m.assignments || []).forEach(a => {
            totalItems++;
            if (a.completed) completedItems++;
        });
        // Exams
        (m.exams || []).forEach(e => {
            totalItems++;
            if (e.passed) completedItems++;
        });
    });

    const isFinished = totalItems > 0 && completedItems === totalItems;

    if (isFinished) {
        certBtn.classList.remove('hidden');
        certBtn.onclick = () => downloadPdfCert(course.id);
    } else {
        certBtn.classList.add('hidden');
    }
}

async function downloadPdfCert(courseId) {
    // Redirect to verification page instead of direct download
    try {
        const course = window.playerCourseData;
        const courseTitle = course ? course.title : 'Course';
        const studentName = localStorage.getItem('userName') || 'Student';

        // Construct URL
        const verifyUrl = `verify-certificate.html?courseId=${courseId}&course=${encodeURIComponent(courseTitle)}&name=${encodeURIComponent(studentName)}`;

        // Redirect
        window.location.href = verifyUrl;

    } catch (e) {
        console.error(e);
        alert('Error redirecting to certificate page.');
    }
}

// showCertificateModal removed as we now redirect to a full page
function shareOnWhatsApp(text, url) {
    window.open(`https://wa.me/?text=${text} ${url}`, '_blank');
}

function shareOnFacebook(url) {
    window.open(`https://www.facebook.com/sharer/sharer.php?u=${url}`, '_blank');
}

function shareOnTwitter(text, url) {
    window.open(`https://twitter.com/intent/tweet?text=${text}&url=${url}`, '_blank');
}

function shareOnLinkedIn(url) {
    window.open(`https://www.linkedin.com/sharing/share-offsite/?url=${url}`, '_blank');
}


function closeCertificateModal() {
    const modal = document.getElementById('certificateModal');
    if (modal) {
        modal.remove();
        if (window.currentCertificateBlob) {
            window.URL.revokeObjectURL(window.currentCertificateBlob);
            delete window.currentCertificateBlob;
            delete window.currentCertificateCourseId;
        }
    }
}

function downloadCertificateFile() {
    if (!window.currentCertificateBlob || !window.currentCertificateCourseId) return;

    const url = window.URL.createObjectURL(window.currentCertificateBlob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `Certificate_${window.currentCertificateCourseId}.pdf`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.URL.revokeObjectURL(url);
    showSuccessMessage('Certificate downloaded successfully!');
}

function shareOnWhatsApp(text, url) {
    const whatsappUrl = `https://wa.me/?text=${text}%20${url}`;
    window.open(whatsappUrl, '_blank', 'width=600,height=400');
}

function shareOnLinkedIn(url) {
    const linkedinUrl = `https://www.linkedin.com/sharing/share-offsite/?url=${url}`;
    window.open(linkedinUrl, '_blank', 'width=600,height=600');
}

function shareOnFacebook(url) {
    const facebookUrl = `https://www.facebook.com/sharer/sharer.php?u=${url}`;
    window.open(facebookUrl, '_blank', 'width=600,height=400');
}

function shareOnTwitter(text, url) {
    const twitterUrl = `https://twitter.com/intent/tweet?text=${text}&url=${url}`;
    window.open(twitterUrl, '_blank', 'width=600,height=400');
}

// Global Mobile Navigation Toggle
document.addEventListener('DOMContentLoaded', () => {
    const toggle = document.querySelector('.nav-toggle');
    const links = document.querySelector('.nav-links');
    if (toggle && links) {
        toggle.addEventListener('click', (e) => {
            e.stopPropagation(); // Prevent immediate closing if clicking the toggle
            links.classList.toggle('open');
        });

        // Close menu when clicking outside
        document.addEventListener('click', (e) => {
            if (links.classList.contains('open') && !links.contains(e.target) && !toggle.contains(e.target)) {
                links.classList.remove('open');
            }
        });
    }
});


window.toggleModule = function (index) {
    const el = document.getElementById(`module-${index}`);
    if (el) {
        el.classList.toggle('active');
    }
}

// Mobile: toggle modules sidebar visibility
window.addEventListener('DOMContentLoaded', () => {
    try {
        const toggleBtn = document.getElementById('toggleModulesBtn');
        const sidebar = document.querySelector('.sidebar-player');
        if (toggleBtn && sidebar) {
            toggleBtn.addEventListener('click', () => {
                sidebar.classList.toggle('collapsed');
            });
        }
    } catch (e) { }
});

function startExam(id, title) {
    console.log('Start Exam clicked:', id, title);
    const urlParams = new URLSearchParams(window.location.search);
    const cid = urlParams.get('id');
    if (!confirm(`Do you want to start the exam "${title}"?`)) return;
    window.location.href = `exam-runner.html?examId=${id}&courseId=${cid}`;
}

let currentVideoId = null;
// Helper to clear all active states
function clearActiveLinks() {
    document.querySelectorAll('.video-link, .assignment-link, .exam-link').forEach(e => e.classList.remove('active'));
}

async function selectVideo(el, id, title, moduleIndex) {
    currentVideoId = id;
    clearActiveLinks();
    if (el) el.classList.add('active');

    // Auto-open module if needed
    const moduleItem = el?.closest('.module-item');
    if (moduleItem && !moduleItem.classList.contains('active')) {
        moduleItem.classList.add('active');
    }

    // Hide empty state, show learning container
    document.getElementById('emptyState').style.display = 'none';
    document.getElementById('learningContainer').classList.remove('hidden');
    document.getElementById('assignmentFullView').classList.add('hidden');

    document.getElementById('videoTitle').innerText = title;

    // Find video data to check completion
    let vData = null;
    if (window.playerCourseData) {
        window.playerCourseData.modules.forEach(m => {
            const found = m.videos?.find(vi => String(vi.id) === String(id));
            if (found) vData = found;
        });
    }
    window.currentVideoCompleted = vData ? vData.completed : false;
    window.maxTimeReached = 0; // Will be set in fetchAndPlay after loading metadata

    // Render module activities below video
    if (moduleIndex !== undefined) {
        renderModuleActivities(moduleIndex);
    }

    await fetchAndPlay();
}

// New function to render activities below video
function renderModuleActivities(moduleIndex) {
    if (!window.playerCourseData || !window.playerCourseData.modules[moduleIndex]) return;

    const module = window.playerCourseData.modules[moduleIndex];
    const activitiesGrid = document.getElementById('moduleActivities');

    let html = '';

    // Render Assignments
    (module.assignments || []).forEach(a => {
        const status = a.completed ? 'completed' : 'pending';
        const statusText = a.completed ? 'Completed' : 'Not Submitted';
        html += `
            <div class="activity-card" onclick="showAssignment('${a.title}', '${a.textContent || a.description || ''}', '${a.type}', '${a.id}', document.getElementById('asn-${a.id}'))">
                <div class="activity-card-header">
                    <div class="activity-icon assignment">
                        <i class="fas fa-file-alt"></i>
                    </div>
                    <div class="activity-card-title">
                        <h4>${a.title}</h4>
                        <p>Assignment • ${a.type || 'Text'}</p>
                    </div>
                </div>
                <div class="activity-status ${status}">
                    <i class="fas fa-${status === 'completed' ? 'check' : 'clock'}-circle"></i>
                    ${statusText}
                </div>
            </div>
        `;
    });

    // Render Exams/Quizzes
    (module.exams || []).forEach(e => {
        const status = e.passed ? 'completed' : (e.completed ? 'submitted' : 'pending');
        const statusText = e.passed ? 'Passed' : (e.completed ? 'Failed' : 'Not Started');
        const isLocked = false; // unlocked
        html += `
            <div class="activity-card ${isLocked ? 'locked' : ''}" 
                 ${isLocked ? '' : `onclick="startExam('${e.id}', '${e.title}')"`}>
                <div class="activity-card-header">
                    <div class="activity-icon quiz">
                        <i class="fas ${isLocked ? 'fa-lock' : 'fa-flask'}"></i>
                    </div>
                    <div class="activity-card-title">
                        <h4>${e.title}</h4>
                        <p>Quiz • ${e.questions?.length || 0} Questions</p>
                    </div>
                </div>
                <div class="activity-status ${isLocked ? 'pending' : status}">
                    <i class="fas fa-${isLocked ? 'lock' : (status === 'completed' ? 'check' : (status === 'submitted' ? 'times' : 'clock'))}-circle"></i>
                    ${isLocked ? 'Locked' : statusText}
                </div>
            </div>
        `;
    });

    if (html === '') {
        html = '<p style="color:#94a3b8;text-align:center;padding:40px;">No activities in this module.</p>';
    }

    activitiesGrid.innerHTML = html;
}

function showAssignment(title, desc, type, id, el) {
    const video = document.getElementById('mainPlayer');
    if (video) video.pause();

    clearActiveLinks();
    if (el) el.classList.add('active');

    // Hide learning container, show assignment full view
    document.getElementById('learningContainer').classList.add('hidden');
    document.getElementById('assignmentFullView').classList.remove('hidden');
    document.getElementById('emptyState').style.display = 'none';

    document.getElementById('activityTitle').innerText = title;

    // Find if completed
    let isCompleted = false;
    if (window.playerCourseData) {
        outer: for (const m of window.playerCourseData.modules) {
            if (m.assignments) {
                for (const a of m.assignments) {
                    if (String(a.id) === String(id)) {
                        isCompleted = a.completed;
                        break outer;
                    }
                }
            }
        }
    }

    let content = desc;
    if (window.playerCourseData) {
        try {
            for (const m of window.playerCourseData.modules) {
                const found = m.assignments?.find(a => String(a.id) === String(id));
                if (found && found.textContent) {
                    content = found.textContent;
                }
            }
        } catch (e) { }
    }

    const html = `
        <div class="assignment-view-container">
            <!-- Meta Row -->
            <div class="asgn-meta-row">
                <div class="asgn-type-badge">
                    <i class="fas fa-tag"></i> ${type} Assignment
                </div>
                <div class="asgn-status-badge ${isCompleted ? 'completed' : 'pending'}">
                    <i class="fas ${isCompleted ? 'fa-check-circle' : 'fa-clock'}"></i>
                    ${isCompleted ? 'Completed' : 'Status: Pending'}
                </div>
            </div>

            <!-- Stats Grid -->
            <div class="asgn-stats-grid">
                <div class="asgn-stat-box">
                    <div class="asgn-stat-icon">
                        <i class="fas fa-calendar-alt"></i>
                    </div>
                    <div class="asgn-stat-content">
                        <span>Due Date</span>
                        <strong>No Deadline</strong>
                    </div>
                </div>
                <div class="asgn-stat-box">
                    <div class="asgn-stat-icon">
                        <i class="fas fa-trophy"></i>
                    </div>
                    <div class="asgn-stat-content">
                        <span>Max Points</span>
                        <strong>Pass / Fail</strong>
                    </div>
                </div>
                <div class="asgn-stat-box">
                    <div class="asgn-stat-icon">
                        <i class="fas fa-users"></i>
                    </div>
                    <div class="asgn-stat-content">
                        <span>Submissions</span>
                        <strong>Unlimited</strong>
                    </div>
                </div>
            </div>

            <!-- Navigation Tabs -->
            <div class="asgn-navigation">
                <div class="nav-tab active" onclick="switchAsgnTab('instructions', event)">Instructions</div>
                <div class="nav-tab" onclick="switchAsgnTab('submission', event)">My Submission</div>
            </div>

            <!-- Tab Content -->
            <div id="asgn-instructions" class="asgn-pane active">
                <div class="instructions-card">
                    <h4><i class="fas fa-book-open"></i> Problem Statement</h4>
                    <div class="problem-description">
                        <p>${content.replace(/\n/g, '<br>')}</p>
                    </div>
                    
                    ${type === 'FILE' ? `
                        <div class="resources-grid">
                            <div class="resource-item-v2">
                                <div class="res-info-left">
                                    <div class="res-icon-box">
                                        <i class="fas fa-file-pdf"></i>
                                    </div>
                                    <div>
                                        <div class="res-title">Assignment_Brief.pdf</div>
                                        <div class="res-subtitle">Resource File • Support</div>
                                    </div>
                                </div>
                                <button class="btn-res-download" onclick="downloadAssignmentFile('${id}')">
                                    <i class="fas fa-download"></i> Download
                                </button>
                            </div>
                        </div>
                    ` : ''}
                </div>
            </div>

            <div id="asgn-submission" class="asgn-pane">
                ${isCompleted ? `
                    <div class="asgn-completed-state">
                        <div class="check-animation">
                            <i class="fas fa-check"></i>
                        </div>
                        <h3 style="font-size: 24px; font-weight: 800; color: #1e293b; margin-bottom: 12px;">Work Submitted!</h3>
                        <p style="color: #64748b; font-size: 16px;">Congratulations! You've successfully submitted this assignment.</p>
                    </div>
                ` : `
                    <div class="submission-card-v2">
                        ${type === 'TEXT' ? `
                            <div style="text-align: left; margin-bottom: 32px;">
                                <h4 style="font-size: 18px; font-weight: 800; color: #1e293b; margin-bottom: 8px;">Detailed Response</h4>
                                <p style="color: #64748b; font-size: 14px;">Use the editor below to provide your complete answer as per the instructions.</p>
                            </div>
                            <textarea id="assignmentAnswer" class="answer-area-v2" placeholder="Start typing your brilliance here..."></textarea>
                            <button class="btn-submit-v2" onclick="submitTextAssignment('${id}')">
                                Submit Work <i class="fas fa-paper-plane"></i>
                            </button>
                        ` : `
                            <div class="upload-icon-v2">
                                <i class="fas fa-cloud-upload-alt"></i>
                            </div>
                            <h4 style="font-size: 22px; font-weight: 800; color: #1e293b; margin-bottom: 12px;">Upload Project Files</h4>
                            <p style="color: #64748b; font-size: 16px; margin-bottom: 40px; max-width: 500px; margin-left: auto; margin-right: auto;">
                                Please bundle your work into a single PDF or ZIP file and upload it here for verification.
                            </p>
                            <div style="padding: 24px; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 20px; display: inline-block; margin-bottom: 32px;">
                                <p style="font-size: 14px; font-weight: 700; color: #64748b; margin: 0;">File upload is currently in maintenance mode.</p>
                            </div>
                            <br>
                            <button class="btn-submit-v2" disabled style="opacity: 0.5; cursor: not-allowed; background: #94a3b8; box-shadow: none;">
                                Submit File <i class="fas fa-lock"></i>
                            </button>
                        `}
                    </div>
                `}
            </div>
        </div>
    `;

    document.getElementById('assignmentContentArea').innerHTML = html;
}

window.switchAsgnTab = function (tab, event) {
    // Update tabs
    document.querySelectorAll('.nav-tab').forEach(t => t.classList.remove('active'));
    if (event) event.target.classList.add('active');

    // Update panes
    document.querySelectorAll('.asgn-pane').forEach(p => p.classList.remove('active'));
    const pane = document.getElementById('asgn-' + tab);
    if (pane) pane.classList.add('active');
}


async function fetchAndPlay() {
    if (!currentVideoId) return;
    const quality = document.getElementById('qualitySelect')?.value || '480p';
    const video = document.getElementById('mainPlayer');

    // Save current playback position for smooth quality switching
    const wasPlaying = !video.paused;
    const currentTime = video.currentTime || 0;

    try {
        // Show loading indicator on quality selector
        const qualitySelect = document.getElementById('qualitySelect');
        const originalQuality = qualitySelect.value;
        qualitySelect.disabled = true;

        const res = await fetchWithAuthNoRedirect(`/student/videos/${currentVideoId}/play?quality=${encodeURIComponent(quality)}`);
        if (res.ok) {
            const data = await res.json();
            document.getElementById('placeholderMsg').style.display = 'none';

            // Change video source
            video.src = data.signedUrl;

            // Restore playback settings
            const sp = localStorage.getItem('player_speed');
            if (sp) { try { video.playbackRate = parseFloat(sp); } catch { } }
            const vol = localStorage.getItem('player_volume');
            if (vol) { try { video.volume = parseFloat(vol); video.muted = video.volume === 0; } catch { } }

            // Wait for metadata to load, then restore position and play state
            video.onloadedmetadata = () => {
                // Restore position
                let startPos = 0;
                if (currentTime > 0) {
                    startPos = currentTime;
                } else {
                    let v = null;
                    if (window.playerCourseData) {
                        window.playerCourseData.modules.forEach(m => {
                            const found = m.videos?.find(vi => String(vi.id) === String(currentVideoId));
                            if (found) v = found;
                        });
                    }

                    if (v && v.lastPosition && v.lastPosition > 0) {
                        startPos = v.lastPosition;
                    } else {
                        const last = localStorage.getItem(`vidpos_${currentVideoId}`);
                        if (last) startPos = parseFloat(last);
                    }
                }

                video.currentTime = startPos;
                window.maxTimeReached = startPos;

                // Resume playing if it was playing before
                if (wasPlaying) {
                    video.play().catch(e => console.log('Autoplay prevented:', e));
                }

                // Re-enable quality selector
                qualitySelect.disabled = false;

                if (currentTime > 0) {
                    showSuccessMessage(`Switched to ${quality}`);
                }
            };

            // Seek blocking logic
            video.onseeking = () => {
                if (false) {
                    video.currentTime = window.maxTimeReached;
                    showSuccessMessage("Please complete the video to seek forward");
                }
            };

            // Handle video completion
            video.onended = async () => {
                try {
                    await fetchWithAuthNoRedirect(`/student/videos/${currentVideoId}/complete`, { method: 'POST' });

                    showSuccessMessage("Lesson Completed!");

                    // Refresh state from backend to unlock next items
                    const urlParams = new URLSearchParams(window.location.search);
                    const cid = urlParams.get('id');
                    if (cid) await loadPlayer(cid);

                    // Certificate check is now inside loadPlayer -> checkCertificateAvailability
                } catch (e) { }
            };

            // Save progress to backend every 5 seconds
            let lastSave = 0;
            video.ontimeupdate = () => {
                // Save to local storage always (fallback)
                localStorage.setItem(`vidpos_${currentVideoId}`, video.currentTime);

                const now = Date.now();
                // Save to backend throttled
                if (now - lastSave > 5000 && !video.paused && !video.ended && currentVideoId) {
                    lastSave = now;
                    fetchWithAuthNoRedirect(`/student/videos/${currentVideoId}/progress?position=${video.currentTime}`, {
                        method: 'POST'
                    }).catch(console.error);
                }

                // Update max watched point
                if (!window.currentVideoCompleted && video.currentTime > window.maxTimeReached) {
                    window.maxTimeReached = video.currentTime;
                }
            };
        } else {
            if (qualitySelect) qualitySelect.disabled = false;
            alert('Could not load video');
        }
    } catch (e) {
        console.error(e);
        const qualitySelect = document.getElementById('qualitySelect');
        if (qualitySelect) qualitySelect.disabled = false;
        alert('Error loading video');
    }
}

// Quality selector change event - trigger quality switch
document.addEventListener('DOMContentLoaded', () => {
    const qualitySelect = document.getElementById('qualitySelect');
    if (qualitySelect) {
        qualitySelect.addEventListener('change', async () => {
            if (currentVideoId) {
                await fetchAndPlay();
            }
        });
    }
});

function playNext() {
    if (!window.playerCourseData || !currentVideoId) return;
    const list = [];
    window.playerCourseData.modules.forEach((m, mIdx) => {
        (m.videos || []).forEach(v => {
            v.moduleIndex = mIdx; // Attach for selectVideo
            list.push(v);
        });
    });
    const idx = list.findIndex(v => String(v.id) === String(currentVideoId));
    if (idx >= 0 && idx < list.length - 1) {
        const next = list[idx + 1];
        if (false) {
            showSuccessMessage('Complete current lesson to unlock next');
            return;
        }
        const el = document.getElementById(`vid-${next.id}`);
        selectVideo(el, next.id, next.title, next.moduleIndex);
        showSuccessMessage('Playing next video');
    } else {
        showSuccessMessage('No next video available');
    }
}

function playPrev() {
    if (!window.playerCourseData || !currentVideoId) return;
    const list = [];
    window.playerCourseData.modules.forEach((m, mIdx) => {
        (m.videos || []).forEach(v => {
            v.moduleIndex = mIdx;
            list.push(v);
        });
    });
    const idx = list.findIndex(v => String(v.id) === String(currentVideoId));
    if (idx > 0) {
        const prev = list[idx - 1];
        const el = document.getElementById(`vid-${prev.id}`);
        selectVideo(el, prev.id, prev.title, prev.moduleIndex);
        showSuccessMessage('Playing previous video');
    } else {
        showSuccessMessage('No previous video available');
    }
}

function formatTime(s) {
    const t = Math.floor(s || 0);
    const h = Math.floor(t / 3600);
    const m = Math.floor((t % 3600) / 60);
    const sec = t % 60;
    if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`;
    return `${m}:${String(sec).padStart(2, '0')}`;
}

function initPlayerControls() {
    const v = document.getElementById('mainPlayer');
    const playBtn = document.getElementById('playBtn');
    const muteBtn = document.getElementById('muteBtn');
    const vol = document.getElementById('volumeRange');
    const timeLabel = document.getElementById('timeLabel');
    const prevBtn = document.getElementById('prevBtn');
    const fsBtn = document.getElementById('fsBtn');
    const pipBtn = document.getElementById('pipBtn');
    const theaterBtn = document.getElementById('theaterBtn');
    const speedSelect = document.getElementById('speedSelect');
    if (!v) return;
    if (playBtn) playBtn.onclick = () => { if (v.paused) v.play(); else v.pause(); };
    if (muteBtn) muteBtn.onclick = () => { v.muted = !v.muted; };
    if (vol) {
        const sv = localStorage.getItem('player_volume');
        if (sv) { try { vol.value = sv; v.volume = parseFloat(sv); v.muted = v.volume === 0; } catch { } }
        vol.oninput = (e) => { v.volume = parseFloat(e.target.value); v.muted = v.volume === 0; try { localStorage.setItem('player_volume', String(v.volume)); } catch { } };
    }
    if (prevBtn) prevBtn.onclick = playPrev;
    if (fsBtn) fsBtn.onclick = () => {
        const container = document.querySelector('.video-container');
        if (!document.fullscreenElement) container.requestFullscreen?.();
        else document.exitFullscreen?.();
    };
    if (pipBtn) pipBtn.onclick = async () => {
        try { if (document.pictureInPictureElement) document.exitPictureInPicture(); else await v.requestPictureInPicture(); } catch { }
    };
    if (theaterBtn) theaterBtn.onclick = () => {
        const pc = document.querySelector('.player-content');
        pc.classList.toggle('theater');
    };
    if (speedSelect) {
        const sp = localStorage.getItem('player_speed');
        if (sp) { try { speedSelect.value = sp; v.playbackRate = parseFloat(sp); } catch { } }
        speedSelect.onchange = (e) => { v.playbackRate = parseFloat(e.target.value); try { localStorage.setItem('player_speed', String(v.playbackRate)); } catch { } };
    }
    v.addEventListener('timeupdate', () => {
        try { if (currentVideoId) localStorage.setItem(`vidpos_${currentVideoId}`, String(v.currentTime)); } catch { }
        if (timeLabel) timeLabel.innerText = `${formatTime(v.currentTime)} / ${formatTime(v.duration || 0)}`;
    });
    v.addEventListener('loadedmetadata', () => {
        if (timeLabel) timeLabel.innerText = `${formatTime(0)} / ${formatTime(v.duration || 0)}`;
    });
    document.addEventListener('keydown', (e) => {
        if (!v) return;
        if (e.key === ' ' || e.key.toLowerCase() === 'k') { e.preventDefault(); if (v.paused) v.play(); else v.pause(); }
        else if (e.key.toLowerCase() === 'j') v.currentTime = Math.max(0, v.currentTime - 10);
        else if (e.key.toLowerCase() === 'l') {
            const nextTime = v.currentTime + 10;
            if (!window.currentVideoCompleted && nextTime > window.maxTimeReached + 1) {
                showSuccessMessage("Finish watching to unlock forward seeking");
            } else {
                v.currentTime = Math.min(v.duration || nextTime, nextTime);
            }
        }
        else if (e.key.toLowerCase() === 'm') v.muted = !v.muted;
        else if (e.key.toLowerCase() === 'f') { const c = document.querySelector('.video-container'); if (!document.fullscreenElement) c.requestFullscreen?.(); else document.exitFullscreen?.(); }
        else if (e.key.toLowerCase() === 'n') playNext();
        else if (e.key.toLowerCase() === 'p') playPrev();
        else if (e.key === 'ArrowRight') {
            const nextTime = v.currentTime + 5;
            if (!window.currentVideoCompleted && nextTime > window.maxTimeReached + 1) {
                showSuccessMessage("Finish watching to unlock forward seeking");
            } else {
                v.currentTime = Math.min(v.duration || nextTime, nextTime);
            }
        }
        else if (e.key === 'ArrowLeft') v.currentTime = Math.max(0, v.currentTime - 5);
    });
}

document.addEventListener('DOMContentLoaded', initPlayerControls);

// Old showAssignment removed


async function submitTextAssignment(id) {
    const ta = document.getElementById('assignmentAnswer');
    if (!ta || !ta.value || ta.value.trim().length === 0) {
        alert('Please write your answer before submitting');
        return;
    }
    try {
        const res = await fetchWithAuth(`/student/assignments/${id}/submit-text`, {
            method: 'POST',
            body: JSON.stringify({ text: ta.value })
        });
        if (res.ok) {
            showSuccessMessage('Assignment submitted successfully');

            // Mark as completed locally
            if (window.playerCourseData) {
                window.playerCourseData.modules.forEach(m => {
                    const a = m.assignments?.find(as => String(as.id) === String(id));
                    if (a) a.completed = true;
                });
            }
            // Update UI
            const el = document.getElementById(`asn-${id}`);
            if (el && !el.querySelector('.fa-check-circle')) {
                el.innerHTML += ' <i class="fas fa-check-circle"></i>';
            }
            checkCertificateAvailability(window.playerCourseData);

        } else {
            alert('Failed to submit: ' + await res.text());
        }
    } catch (e) {
        console.error(e);
        alert('Error submitting assignment');
    }
}
async function downloadAssignmentFile(id) {
    try {
        const res = await fetchWithAuth(`/student/assignments/${id}/file`);
        if (res.ok) {
            const data = await res.json();
            const a = document.createElement('a');
            a.href = data.fileUrl;
            a.target = '_blank';
            a.rel = 'noopener';
            a.click();
        } else {
            alert('Could not fetch file');
        }
    } catch (e) {
        console.error(e);
        alert('Error fetching file');
    }
}

function logout() {
    localStorage.removeItem('token');
    window.location.href = 'index.html';
}


// ========== LANDING PAGE FUNCTIONS ==========

let allCoursesData = [];
let currentFilter = 'all';

async function loadAllCoursesForHome() {
    try {
        const res = await fetch('/api/student/all-courses');
        if (res.ok) {
            allCoursesData = await res.json();
            displayCourses(allCoursesData);
            displayFreeCourses(allCoursesData);
        } else {
            document.getElementById('allCoursesGrid').innerHTML = '<p class="loading-msg">Failed to load courses</p>';
        }
    } catch (e) {
        console.error(e);
        document.getElementById('allCoursesGrid').innerHTML = '<p class="loading-msg">Error loading courses</p>';
    }
}

function displayCourses(courses) {
    const grid = document.getElementById('allCoursesGrid');
    if (!grid) return;

    if (courses.length === 0) {
        grid.innerHTML = '<p class="loading-msg">No courses available yet</p>';
        return;
    }

    grid.innerHTML = courses.map(c => `
        <div class="course-card" onclick="goToLogin()">
            <img src="${c.thumbnail ? c.thumbnail : 'https://images.unsplash.com/photo-1516321318423-f06f85e504b3?w=800&q=80'}" 
                 alt="${c.title}" 
                 onerror="this.src='https://images.unsplash.com/photo-1516321318423-f06f85e504b3?w=800&q=80'">
            <div class="info">
                <span class="course-badge">Online</span>
                <h3 class="course-title">${c.title}</h3>
                <div class="course-meta">
                    <span><i class="fas fa-star"></i> (5 Reviews)</span>
                    <span>Language: ${c.language || 'English'}</span>
                </div>
                <div class="course-meta">
                    <span><i class="fas fa-clock"></i> Duration: ${c.duration || 3} Months</span>
                </div>
                <div class="course-price ${c.price == 0 ? 'free' : ''}">
                    ${c.price == 0 ? 'Rs.0.00' : 'Rs.' + c.price + '.00'}
                    ${c.mrp && c.mrp > c.price ? `<span style="text-decoration: line-through; font-size: 16px; color: #94a3b8; margin-left: 8px;">Rs.${c.mrp}.00</span>` : ''}
                </div>
                <div class="course-actions">
                    <button class="btn-enroll" onclick="event.stopPropagation(); goToLogin();">Enroll Now →</button>
                    <button class="btn-view" onclick="event.stopPropagation(); goToLogin();">
                        <i class="fas fa-eye"></i> View
                    </button>
                </div>
            </div>
        </div>
    `).join('');
}

function displayFreeCourses(courses) {
    const grid = document.getElementById('freeCoursesGrid');
    if (!grid) return;

    const freeCourses = courses.filter(c => c.price == 0).slice(0, 3);

    if (freeCourses.length === 0) {
        grid.innerHTML = '<p class="loading-msg">No free courses available yet</p>';
        return;
    }

    grid.innerHTML = freeCourses.map(c => `
        <div class="course-card" onclick="goToLogin()">
            <img src="${c.thumbnail || 'https://via.placeholder.com/350x220/10b981/ffffff?text=' + encodeURIComponent(c.title)}" 
                 alt="${c.title}" 
                 onerror="this.src='https://via.placeholder.com/350x220/10b981/ffffff?text=Free+Course'">
            <div class="info">
                <span class="course-badge" style="background: #d1fae5; color: #059669;">Pre-Recorded</span>
                <h3 class="course-title">${c.title}</h3>
                <div class="course-meta">
                    <span><i class="fas fa-star"></i> (5 Reviews)</span>
                    <span><i class="fas fa-clock"></i> Duration: ${c.duration || 1} Month</span>
                </div>
                <div class="course-price free">Rs.0.00 <span style="text-decoration: line-through; font-size: 16px; color: #94a3b8;">Rs.${c.mrp || 999}.00</span></div>
                <div class="course-actions">
                    <button class="btn-enroll" onclick="event.stopPropagation(); goToLogin();">Enroll Now →</button>
                    <button class="btn-view" onclick="event.stopPropagation(); goToLogin();">
                        <i class="fas fa-eye"></i> View
                    </button>
                </div>
            </div>
        </div>
    `).join('');
}

function filterByType(type) {
    currentFilter = type;

    // Update active tab
    document.querySelectorAll('.filter-tab').forEach(tab => tab.classList.remove('active'));
    event.target.classList.add('active');

    let filtered = allCoursesData;
    if (type === 'online') {
        filtered = allCoursesData.filter(c => c.price > 0);
    } else if (type === 'recorded') {
        filtered = allCoursesData.filter(c => c.price == 0);
    }

    displayCourses(filtered);
}

function filterCourses() {
    const searchTerm = document.getElementById('searchInput').value.toLowerCase();
    const filtered = allCoursesData.filter(c =>
        c.title.toLowerCase().includes(searchTerm) ||
        (c.description && c.description.toLowerCase().includes(searchTerm)) ||
        (c.category && c.category.toLowerCase().includes(searchTerm))
    );
    displayCourses(filtered);
}

function goToLogin() {
    window.location.href = 'index.html';
}
// Make functions globally accessible for onclick handlers
window.enroll = enroll;
window.playNext = playNext;
window.playPrev = playPrev;
window.loadStudentDashboard = loadStudentDashboard;
window.loadAllCoursesForHome = loadAllCoursesForHome;
window.selectVideo = selectVideo;
window.startExam = startExam;
window.showAssignment = showAssignment;
window.submitTextAssignment = submitTextAssignment;
window.downloadAssignmentFile = downloadAssignmentFile;
window.logout = logout;
window.downloadPdfCert = downloadPdfCert;
window.toggleModule = toggleModule;

// Sidebar Toggle Logic Persistence & Initialization
function initSidebarToggle() {
    const toggleBtn = document.getElementById('sidebarToggleBtn');
    const layout = document.querySelector('.player-layout');

    if (toggleBtn && layout) {
        // Load preference
        const isCollapsed = localStorage.getItem('playerSidebarCollapsed') === 'true';
        if (isCollapsed) {
            layout.classList.add('sidebar-hidden');
            const icon = toggleBtn.querySelector('i');
            if (icon) icon.className = 'fas fa-chevron-right';
        }

        toggleBtn.addEventListener('click', () => {
            layout.classList.toggle('sidebar-hidden');
            const hidden = layout.classList.contains('sidebar-hidden');

            // Save preference
            localStorage.setItem('playerSidebarCollapsed', hidden);

            // Update Icon
            const icon = toggleBtn.querySelector('i');
            if (icon) {
                icon.className = hidden ? 'fas fa-chevron-right' : 'fas fa-chevron-left';
            }
        });
    }
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initSidebarToggle);
} else {
    initSidebarToggle();
}

async function loadStudentProfile() {
    try {
        const response = await fetch('/api/student/profile', {
            headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
        });
        if (!response.ok) throw new Error('Failed to load profile');
        const profile = await response.json();

        // Update UI
        document.getElementById('profileName').value = profile.name || '';
        document.getElementById('profileEmail').value = profile.email || '';
        document.getElementById('profileAddress').value = profile.address || '';
        document.getElementById('profilePhone').value = profile.phone || '';
        document.getElementById('displayName').textContent = profile.name || 'Student';
        document.getElementById('displayEmail').textContent = profile.email || '';

        if (profile.profilePictureUrl) {
            document.getElementById('profileImage').src = profile.profilePictureUrl;
        }

        // Setup Event Listeners
        const profileForm = document.getElementById('profileForm');
        profileForm.onsubmit = async (e) => {
            e.preventDefault();
            await updateProfile();
        };

        const photoUpload = document.getElementById('photoUpload');
        photoUpload.onchange = async (e) => {
            if (e.target.files.length > 0) {
                await uploadProfilePicture(e.target.files[0]);
            }
        };

        const btnRequestOtp = document.getElementById('btnRequestOtp');
        btnRequestOtp.onclick = async () => await requestPhoneOtp();

        const btnVerifyOtp = document.getElementById('btnVerifyOtp');
        btnVerifyOtp.onclick = async () => await verifyPhoneOtp();

    } catch (error) {
        console.error('Error:', error);
        alert('Could not load profile. Please try again.');
    }
}

async function updateProfile() {
    const request = {
        name: document.getElementById('profileName').value,
        email: document.getElementById('profileEmail').value,
        address: document.getElementById('profileAddress').value
    };

    try {
        const response = await fetch('/api/student/profile', {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${localStorage.getItem('token')}`
            },
            body: JSON.stringify(request)
        });
        if (!response.ok) throw new Error('Update failed');
        alert('Profile updated successfully!');
        loadStudentProfile(); // Refresh
    } catch (error) {
        alert(error.message);
    }
}

async function uploadProfilePicture(file) {
    const formData = new FormData();
    formData.append('file', file);

    try {
        const response = await fetch('/api/student/profile/picture', {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` },
            body: formData
        });
        if (!response.ok) throw new Error('Upload failed');
        alert('Photo updated!');
        loadStudentProfile(); // Refresh to see new photo
    } catch (error) {
        alert(error.message);
    }
}

async function requestPhoneOtp() {
    const newPhone = document.getElementById('profilePhone').value;
    if (!newPhone || newPhone.length < 10) {
        alert('Please enter a valid 10-digit number');
        return;
    }

    try {
        const response = await fetch('/api/student/profile/phone/request', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${localStorage.getItem('token')}`
            },
            body: JSON.stringify({ newPhone })
        });
        if (!response.ok) {
            const err = await response.json();
            throw new Error(err.message || 'Failed to send OTP');
        }
        document.getElementById('otpSection').classList.remove('hidden');
        alert('OTP sent to ' + newPhone);
    } catch (error) {
        alert(error.message);
    }
}

async function verifyPhoneOtp() {
    const newPhone = document.getElementById('profilePhone').value;
    const otp = document.getElementById('phoneOtp').value;

    try {
        const response = await fetch('/api/student/profile/phone/verify', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${localStorage.getItem('token')}`
            },
            body: JSON.stringify({ newPhone, otp })
        });
        if (!response.ok) throw new Error('Verification failed. Invalid OTP?');
        alert('Phone number updated successfully!');
        document.getElementById('otpSection').classList.add('hidden');
        loadStudentProfile(); // Refresh
    } catch (error) {
        alert(error.message);
    }
}

window.loadStudentProfile = loadStudentProfile;

