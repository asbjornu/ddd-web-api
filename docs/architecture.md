# Spring Boot Application with code smells

Write a web application with code smells building on the ideas presented
in the talk [What's In A Model][1]. The goal of the application is to
demonstrate in particular how providing a REST-ish CRUD API that mismatches
the underlying domain model can lead to code being repeated between
frontend, bff API, and service API, and how this can lead to code smells in
the application.

In the future, this `architecture.md` file will be rewritten to describe a
a more optimal architecture that the application should then be slowly
refactored towards. The goal is to demonstrate how to identify the the
real-world problems that arise from the mismatch and code smells first
introduced, and how the proposed RESTful, hypermedia-driven, Domain-driven
designed architecture can help address and alleviate them.

## Code smells

Make sure to include the following code smells:

- **God Object**: A class that knows too much or does too much.
- **Feature Envy**: A method that seems more interested in a class other
  than the one it actually is in.
- **Data Clumps**: A group of variables that are always passed around
  together.
- **Primitive Obsession**: Using primitive data types to represent domain
  ideas instead of creating small objects for them.
- **Long Method**: A method that is too long and tries to do too much.
- **Switch Statements**: Using switch statements instead of polymorphism.
- **Speculative Generality**: Code that is more general than it needs to
  be, often because the developer anticipates future requirements that may
  never come.
- **Temporary Field**: A field that is only set in certain circumstances,
  leading to confusion about its purpose.
- **Refused Bequest**: A subclass that inherits methods and properties from
  a parent class but does not use them, leading to confusion about the
  relationship between the classes.
- **Model reuse**: Reusing the same model class for several different
  purposes such as ORM, DTO, API representation, validation and domain
  business logic.
- **Inappropriate Intimacy**: Two classes that are too closely related and
  know too much about each other, leading to tight coupling and difficulty
  in maintaining the code.
- **Inconsistent Naming**: Using inconsistent naming conventions for
  classes, methods, and variables, leading to confusion and difficulty in
  understanding the code.
- **Inappropriate Use of Static**: Using static methods or variables in
  appropriately, leading to tight coupling and difficulty in testing and
  maintaining the code.
- **Inappropriate Use of Inheritance**: Using inheritance inappropriately,
  leading to confusion about the relationship between classes and
  difficulty in maintaining the code.

## Architecture

The application should consist of three separate layers described below.

### Domain

The business domain of the application can be anything you like, but it
should be simple enough to allow for the inclusion of the code smells
listed above.

### Front-end

The front-end should be a single-page application (SPA) written in Vue.js
that consumes a REST-ish backend for frontend API. The code should be
written in Typescript with Vue 3 Composition API and Pinia for state
management. Create a linting configuration that suits the application and
enforce it with GitHub Actions. The front-end should include unit tests and
end-to-end tests that demonstrate the code smells in action.

### Backend for frontend API

The backend for frontend API should be written in Nuxt.js and serve the
front-end. It should provide a REST-ish CRUD interface that almost, but not
quite, matches that of the underlying "service API". The mismatch should be
intentional and should be designed to demonstrate the code smells in action.

### Service API

The service API should be written in modern Java and Spring Boot 3. It
should provide a REST-ish API with CRUD operations that mismatch the
underlying domain model, and it should include a unit tests that
demonstrate the code smells in action. The application should be structured
in a way that makes it easy to identify the code smells.

### Database

The application should use an in-memory database such as SQLite or H2 for
development and testing purposes. The database schema should be designed to
support the business domain and should include tables that correspond to
the domain models so they can be reused and misused.

### Repository and file structure

Use a monorepo for the application, with separate directories for the
front-end, backend for frontend API, and service API.

The file structure should be based around types (models, controllers,
services, repositories) rather than features (e.g. "users", "orders",
etc.).

Once the initial architecture is in place, create an AGENTS.md file that
describes the application architecture to AI agents. Provide
project-specific instructions: coding conventions, folder structure, how to
run tests, how to run the dev server, things not to touch. This is the
highest-leverage file for consistency across sessions.

## Devops

The application should be containerized using Docker Compose, with separate
containers for the front-end, backend for frontend API, and service API.
The application should be deployable to a cloud provider of your choice,
and it should include a CI/CD pipeline that runs tests and deploys the
application automatically.

GitHub Actions should be used to run tests and enforce linting rules on
each commit.

## Incremental development

The application should be developed incrementally, with each layer being
developed with accompanying tests before moving on to the next layer.

Features should be added to the service API first, followed by the backend
for frontend API, and finally the front-end. Each layer should be developed
in a way that allows for the inclusion of the code smells listed above.

Commit code after each coherent change, and make sure to include a commit
message that describes the change and the code smell that was introduced.
This will allow for easy identification of the code smells in the commit
history.

After each commit, pause and wait for human confirmation that the change is
complete before moving on to the next change. While pausing, take time to
reflect on the code smell that was introduced, and consider how it could be
refactored to improve the design of the application. This will help to
reinforce the concepts presented in the talk and provide a deeper
understanding of how to identify and address code smells in real-world
applications.

As development progresses, keep readme.md up to date with setup
instructions, stack overview, and how to run locally.

## Definition of done

The application can be considered done when all features are implemented
in all three layers of the application, it includes the code smells
listed above, and it is structured in a way that makes it easy to identify
them. All parts of the application should be runnable and should include
instructions for how to run.

[1]: https://github.com/asbjornu/whats-in-a-model
