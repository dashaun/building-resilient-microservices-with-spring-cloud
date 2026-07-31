(function () {
    'use strict';

    var API_SURVEY = '/survey-service';
    var voterId = crypto.randomUUID();
    var bearerToken = null; // fetched from the gateway's dev /token endpoint
    var pollsContainer = document.getElementById('polls-container');

    // The gateway requires a JWT to POST a vote, so grab one up front.
    fetchToken().then(fetchQuestions);

    function fetchToken() {
        return fetch('/token')
            .then(function (res) { return res.ok ? res.json() : null; })
            .then(function (body) { if (body) bearerToken = body.access_token; })
            .catch(function () { /* reads still work without a token */ });
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

        var headers = { 'Content-Type': 'application/json' };
        if (bearerToken) headers['Authorization'] = 'Bearer ' + bearerToken;

        fetch(API_SURVEY + '/submit', {
            method: 'POST',
            headers: headers,
            body: JSON.stringify({ questionId: questionId, answer: answer, voterId: voterId })
        })
        .then(function (res) {
            if (res.status === 429) throw new Error('Slow down — rate limited');
            if (res.status === 401) throw new Error('Not authorized (missing token)');
            if (!res.ok) throw new Error('HTTP ' + res.status);
            showToast('Vote counted! 🍖', 'success');
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
        toast.textContent = message;
        document.body.appendChild(toast);
        requestAnimationFrame(function () { toast.classList.add('show'); });
        setTimeout(function () {
            toast.classList.remove('show');
            setTimeout(function () { document.body.removeChild(toast); }, 300);
        }, 3000);
    }

})();
