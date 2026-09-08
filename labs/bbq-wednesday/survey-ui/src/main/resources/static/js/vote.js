(function () {
    'use strict';

    var API_SURVEY = '/survey-service';
    // getRandomValues also works on HTTP LAN addresses used by workshop phones.
    var voterId = Array.from(crypto.getRandomValues(new Uint8Array(16)), function (b) {
        return b.toString(16).padStart(2, '0');
    }).join('');
    var bearerToken = null; // fetched from the gateway's dev /token endpoint
    var pollsContainer = document.getElementById('polls-container');

    // The gateway requires a JWT to POST a vote, so grab one up front.
    fetchQuestions();

    function fetchToken() {
        return fetch('/token')
            .then(function (res) {
                if (!res.ok) throw new Error('Cannot get a voting token. Try again.');
                return res.json();
            })
            .then(function (body) { bearerToken = body.access_token; });
    }

    function fetchQuestions() {
        fetch(API_SURVEY + '/questions')
            .then(function (res) {
                if (!res.ok) throw new Error('HTTP ' + res.status);
                return res.json();
            })
            .then(function (questions) {
                pollsContainer.innerHTML = '';
                questions.forEach(renderVoteForm);
            })
            .catch(function (err) {
                pollsContainer.innerHTML =
                    '<p class="loading">Error loading questions: ' + err.message + '</p>';
            });
    }

    function renderVoteForm(question) {
        var card = document.createElement('div');
        card.className = 'question-card';

        var title = document.createElement('h3');
        title.textContent = question.text;
        card.appendChild(title);

        var form = document.createElement('form');
        form.dataset.questionId = question.id;

        question.answers.forEach(function (answer) {
            var option = document.createElement('div');
            option.className = 'answer-option';

            var radio = document.createElement('input');
            radio.type = 'radio';
            radio.name = 'answer-' + question.id;
            radio.value = answer;
            radio.id = 'radio-' + question.id + '-' + answer;

            var label = document.createElement('label');
            label.htmlFor = radio.id;
            label.textContent = answer;

            option.appendChild(radio);
            option.appendChild(label);
            form.appendChild(option);
        });

        var btn = document.createElement('button');
        btn.type = 'submit';
        btn.className = 'submit-btn';
        btn.textContent = 'Submit Vote';
        form.appendChild(btn);

        form.addEventListener('submit', function (e) {
            e.preventDefault();
            var selected = form.querySelector('input[type="radio"]:checked');
            if (!selected) {
                showToast('Pick an answer first', 'error');
                return;
            }
            submitVote(question.id, selected.value, btn, form);
        });

        card.appendChild(form);
        pollsContainer.appendChild(card);
    }

    function submitVote(questionId, answer, btn, form) {
        btn.disabled = true;
        btn.textContent = 'Submitting…';

        // Refresh before voting: a workshop lasts longer than the one-hour token.
        fetchToken().then(function () {
        var headers = { 'Content-Type': 'application/json' };
        if (bearerToken) headers['Authorization'] = 'Bearer ' + bearerToken;

        return fetch(API_SURVEY + '/submit', {
            method: 'POST',
            headers: headers,
            body: JSON.stringify({ questionId: questionId, answer: answer, voterId: voterId })
        });
        })
        .then(function (res) {
            if (res.status === 429) throw new Error('Slow down — rate limited');
            if (res.status === 401) throw new Error('Not authorized (missing token)');
            if (!res.ok) throw new Error('HTTP ' + res.status);
            showToast('Vote submitted! 🍖', 'success');
            form.querySelectorAll('input[type="radio"]').forEach(function (r) { r.checked = false; });
        })
        .catch(function (err) {
            showToast(err.message, 'error');
        })
        .finally(function () {
            btn.disabled = false;
            btn.textContent = 'Submit Vote';
        });
    }

    function showToast(message, type) {
        var toast = document.createElement('div');
        toast.className = 'toast ' + type;
        toast.setAttribute('role', type === 'error' ? 'alert' : 'status');
        toast.textContent = message;
        document.body.appendChild(toast);
        requestAnimationFrame(function () { toast.classList.add('show'); });
        setTimeout(function () {
            toast.classList.remove('show');
            setTimeout(function () { document.body.removeChild(toast); }, 300);
        }, 3000);
    }

})();
